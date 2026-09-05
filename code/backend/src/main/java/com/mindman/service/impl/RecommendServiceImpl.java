package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mindman.dto.ArticleVO;
import com.mindman.entity.Article;
import com.mindman.entity.ChatMessage;
import com.mindman.entity.EmotionRecord;
import com.mindman.mapper.ArticleCategoryMapper;
import com.mindman.mapper.ArticleMapper;
import com.mindman.mapper.ChatMessageMapper;
import com.mindman.mapper.EmotionRecordMapper;
import com.mindman.service.RecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐算法实现。
 *
 * <p>打分公式（每篇文章）：</p>
 * <pre>
 *   total = 0.55 * tagScore  // 与用户情绪画像重合度
 *         + 0.30 * freshScore // 60 天线性新鲜度
 *         + 0.10 * popScore   // 阅读量归一化
 *         + 0.05 * basePop
 *         + realtimeBonus      // 实时文章 +0.4
 * </pre>
 *
 * <p>用户画像：</p>
 * <ul>
 *   <li>近 7 天 {@code emotion_record.emotion} 出现频次 Top 3</li>
 *   <li>近 7 天 {@code chat_message.emotion} 出现频次 Top 3</li>
 *   <li>按内置 emotionMap 抽取相关标签词，构成画像词袋</li>
 * </ul>
 *
 * <p>多样性：保证实时分类至少 1 篇；同分类不重复（实时分类除外可 2 篇）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final ArticleMapper articleMapper;
    private final ArticleCategoryMapper categoryMapper;
    private final EmotionRecordMapper emotionRecordMapper;
    private final ChatMessageMapper chatMessageMapper;

    /** 实时分类 ID（与 article_category.id=7 对应） */
    private static final long CATEGORY_LIVE_ID = 7L;

    /** 用户画像天数 */
    private static final int PROFILE_DAYS = 7;

    /**
     * 情绪词 → 中文标签桶。把 "焦虑/失眠/压抑" 等映射成与文章 emotion_tags 对齐的标签集合。
     */
    private static final Map<String, List<String>> EMOTION_TOKEN_MAP = new LinkedHashMap<>() {{
        put("焦虑",   List.of("焦虑", "紧张", "情绪"));
        put("紧张",   List.of("紧张", "焦虑", "压力"));
        put("压力",   List.of("压力", "焦虑", "疲惫"));
        put("压抑",   List.of("压抑", "情绪", "焦虑"));
        put("难过",   List.of("抑郁", "低落", "情绪"));
        put("抑郁",   List.of("抑郁", "低落", "情绪"));
        put("愤怒",   List.of("愤怒", "人际", "关系"));
        put("委屈",   List.of("人际", "关系", "情绪"));
        put("失眠",   List.of("失眠", "睡眠", "疲惫"));
        put("熬夜",   List.of("失眠", "睡眠", "疲惫"));
        put("疲惫",   List.of("疲惫", "睡眠", "压力"));
        put("开心",   List.of("成长", "人际", "自我"));
        put("迷茫",   List.of("成长", "迷茫", "自我"));
        put("烦躁",   List.of("压力", "焦虑", "人际"));
        put("自卑",   List.of("自卑", "自我", "成长"));
        put("内卷",   List.of("职场", "压力", "倦怠"));
        put("倦怠",   List.of("倦怠", "职场", "疲惫"));
        put("亲密",   List.of("亲密", "关系", "人际"));
        put("家庭",   List.of("家庭", "关系", "人际"));
        put("人际",   List.of("人际", "关系", "压力"));
        put("朋友",   List.of("友情", "人际", "关系"));
        put("工作",   List.of("职场", "压力", "焦虑"));
        put("其他",   List.of("成长", "自我"));
    }};

    @Override
    public List<ArticleVO> recommend(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);

        // 1. 所有已发布文章
        List<Article> all = articleMapper.selectList(
                new QueryWrapper<Article>().eq("status", 1).eq("deleted", 0));
        if (all.isEmpty()) return List.of();

        // 2. 用户词袋
        Set<String> userTokens = buildUserTokenSet(userId);

        // 3. 阅读量归一化基准
        long maxReads = Math.max(1, all.stream().mapToLong(Article::getReads).max().orElse(1));

        // 4. 加载分类名映射
        Map<Long, String> catNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();

        // 5. 打分
        List<ScoredArticle> scored = new ArrayList<>();
        for (Article a : all) {
            Set<String> articleTags = splitTags(a.getEmotionTags());
            double tagScore;
            if (!articleTags.isEmpty() && !userTokens.isEmpty()) {
                Set<String> inter = new HashSet<>(articleTags);
                inter.retainAll(userTokens);
                tagScore = (double) inter.size() / Math.max(articleTags.size(), 1);
            } else {
                // 新用户 / 文章无标签：基础分
                tagScore = userTokens.isEmpty() ? 0.4 : 0.0;
            }

            LocalDateTime pt = Optional.ofNullable(a.getPublishTime()).orElse(now);
            long days = Math.max(0, java.time.Duration.between(pt, now).toDays());
            double freshScore = Math.max(0.0, 1.0 - days / 60.0);

            double popScore = Math.log1p(Math.max(0, a.getReads())) / Math.log1p(Math.max(1, maxReads));

            double realtimeBonus = (a.getCategoryId() != null
                    && a.getCategoryId() == CATEGORY_LIVE_ID) ? 0.4 : 0.0;

            double total = 0.55 * tagScore + 0.30 * freshScore + 0.10 * popScore + 0.05 + realtimeBonus;
            scored.add(new ScoredArticle(a, total));
        }

        // 6. 多样性 pick：实时分类至少 1 篇，分类去重
        List<ArticleVO> result = new ArrayList<>();
        Set<Long> usedArt = new HashSet<>();
        Set<Long> usedCat = new HashSet<>();

        // 先挑 1 篇实时分类
        scored.stream()
                .filter(s -> s.article.getCategoryId() != null
                        && s.article.getCategoryId() == CATEGORY_LIVE_ID)
                .max(Comparator.comparingDouble(s -> s.score))
                .ifPresent(top -> {
                    result.add(toVO(top.article, catNames));
                    usedArt.add(top.article.getId());
                    if (top.article.getCategoryId() != null) usedCat.add(top.article.getCategoryId());
                });

        // 按分数填充，同分类不重复（实时分类可再加 1）
        scored.stream()
                .sorted(Comparator.<ScoredArticle>comparingDouble(s -> s.score).reversed())
                .forEach(s -> {
                    if (result.size() >= safeLimit) return;
                    if (usedArt.contains(s.article.getId())) return;
                    Long catId = s.article.getCategoryId();
                    if (usedCat.contains(catId) && catId != null && catId != CATEGORY_LIVE_ID) return;
                    if (usedCat.contains(catId) && catId != null && catId == CATEGORY_LIVE_ID
                            && result.stream().filter(v -> v.getCategoryId() != null
                                    && v.getCategoryId() == CATEGORY_LIVE_ID).count() >= 2) {
                        // 实时分类也最多 2 篇
                        return;
                    }
                    result.add(toVO(s.article, catNames));
                    usedArt.add(s.article.getId());
                    if (catId != null) usedCat.add(catId);
                });

        // 兜底
        if (result.size() < safeLimit) {
            scored.stream()
                    .sorted(Comparator.<ScoredArticle>comparingDouble(s -> s.score).reversed())
                    .forEach(s -> {
                        if (result.size() >= safeLimit) return;
                        if (usedArt.contains(s.article.getId())) return;
                        result.add(toVO(s.article, catNames));
                        usedArt.add(s.article.getId());
                    });
        }

        return result;
    }

    private ArticleVO toVO(Article a, Map<Long, String> catNames) {
        ArticleVO vo = new ArticleVO();
        vo.setArticleId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setCategoryId(a.getCategoryId());
        vo.setCategoryName(a.getCategoryId() == null ? "未分类"
                : catNames.getOrDefault(a.getCategoryId(), "未分类"));
        vo.setCover(a.getCover());
        vo.setSummary(a.getSummary());
        vo.setTags(a.getTags());
        vo.setAuthor(a.getAuthor());
        vo.setReads(a.getReads());
        vo.setStatus(a.getStatus());
        vo.setPublishTime(a.getPublishTime());
        vo.setSourceType(a.getSourceType());
        vo.setSourceUrl(a.getSourceUrl());
        vo.setSourceName(a.getSourceName());
        return vo;
    }

    private Set<String> buildUserTokenSet(Long userId) {
        if (userId == null) return Set.of();
        Set<String> tokens = new HashSet<>();
        LocalDate fromDate = LocalDate.now().minusDays(PROFILE_DAYS);

        // emotion_record
        try {
            List<EmotionRecord> recs = emotionRecordMapper.selectList(
                    new QueryWrapper<EmotionRecord>()
                            .eq("user_id", userId)
                            .ge("record_date", fromDate));
            Map<String, Long> freq = recs.stream()
                    .filter(r -> r.getEmotion() != null && !r.getEmotion().isBlank())
                    .collect(Collectors.groupingBy(EmotionRecord::getEmotion, Collectors.counting()));
            freq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .forEach(e -> {
                        List<String> ts = EMOTION_TOKEN_MAP.get(e.getKey());
                        if (ts != null) tokens.addAll(ts);
                    });
        } catch (Exception e) {
            log.warn("buildUserTokenSet emotion_record failed: {}", e.getMessage());
        }

        // chat_message.emotion
        try {
            LocalDateTime fromDt = fromDate.atStartOfDay();
            List<ChatMessage> msgs = chatMessageMapper.selectList(
                    new QueryWrapper<ChatMessage>()
                            .eq("role", "assistant")
                            .ge("created_at", fromDt)
                            .isNotNull("emotion"));
            Map<String, Long> freq = msgs.stream()
                    .filter(m -> m.getEmotion() != null && !m.getEmotion().isBlank())
                    .collect(Collectors.groupingBy(ChatMessage::getEmotion, Collectors.counting()));
            freq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .forEach(e -> {
                        List<String> ts = EMOTION_TOKEN_MAP.get(e.getKey());
                        if (ts != null) tokens.addAll(ts);
                    });
        } catch (Exception e) {
            log.debug("buildUserTokenSet chat_message failed: {}", e.getMessage());
        }

        return tokens;
    }

    private Set<String> splitTags(String emotionTags) {
        if (emotionTags == null || emotionTags.isBlank()) return Set.of();
        return Arrays.stream(emotionTags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static final class ScoredArticle {
        final Article article;
        final double score;
        ScoredArticle(Article a, double s) {
            this.article = a;
            this.score = s;
        }
    }
}
