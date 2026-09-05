package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mindman.entity.Article;
import com.mindman.mapper.ArticleMapper;
import com.mindman.service.RealtimeCrawlerService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 实时心理文章爬虫（混合策略）。
 *
 * <h3>两种来源</h3>
 * <ol>
 *   <li><strong>Jina Reader 抓取</strong>：调 {@code https://r.jina.ai/URL} 把页面转 Markdown；
 *       在境内网络可能不通，做兜底</li>
 *   <li><strong>AI 生成兜底</strong>：调百炼 Qwen 针对心理主题生成原创短文。
 *       抓取成功则用抓取的，全部失败则改用 AI 生成</li>
 * </ol>
 *
 * <p>入库统一标记为 {@code source_type='crawled'}、
 * {@code source_url} 记录来源（爬到的是真链接，AI 的是标识）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeCrawlerImpl implements RealtimeCrawlerService {

    private static final long CATEGORY_LIVE = 7L;

    /** 心理主题关键词（命中至少 2 个过滤） */
    private static final List<String> TOPIC_KEYWORDS = List.of(
            "焦虑", "抑郁", "失眠", "情绪", "压力", "紧张",
            "亲密", "关系", "原生家庭", "人际", "友情", "亲情", "恋爱", "孤独",
            "自我", "成长", "自卑", "自卑感", "心理", "疗愈", "抑郁情绪",
            "焦虑情绪", "情感", "情绪管理", "亲密关系", "原生家庭疗愈",
            "愤怒", "委屈", "烦躁"
    );

    /** AI 生成主题池。每天从里面随机选 3-5 个。 */
    private static final List<String> GENERATION_TOPICS = List.of(
            "焦虑情绪的 5 个放松小技巧",
            "当代年轻人失眠自救指南",
            "如何在亲密关系中保持健康的边界",
            "原生家庭带来的影响,如何自我疗愈",
            "识别并缓解工作场所的倦怠",
            "高敏感人群的心理自处之道",
            "社交孤独感怎么破:你的独处与他处",
            "为什么我们总是想讨好别人",
            "情绪稳定的练习:不被他人的情绪带走",
            "如何与抑郁情绪共处:倾听它,而非对抗它",
            "亲密关系中的非暴力沟通",
            "为什么总觉得自己不够好:从完美主义到自我接纳",
            "情绪日记:每天 5 分钟学会安抚自己",
            "当愤怒来袭:3 个不会被情绪控制的小练习"
    );

    /** 外部来源种子池（外网可达时启用） */
    private static final List<Seed> SEEDS = List.of(
            new Seed("KnowYourself",       "https://www.xinli001.com/"),
            new Seed("知乎心理学话题", "https://www.zhihu.com/topic/19551432/top_answers"),
            new Seed("简单心理",         "https://www.jiandanxinli.com/"),
            new Seed("豆瓣心理阅读",     "https://book.douban.com/chart?cat=7")
    );

    private final ArticleMapper articleMapper;
    private final WebClient.Builder webClientBuilder;

    /** 注入 AiConfig 创建的百炼 webclient，做 AI 生成兜底 */
    @Qualifier("siliconFlowWebClient")
    @Resource(name = "siliconFlowWebClient")
    private WebClient aiWebClient;

    @Override
    public int crawlOnce(int limit) {
        int target = Math.min(Math.max(limit, 1), 10);
        int saved = 0;

        // Phase 1：尝试 Jina Reader 抓取（外网通常不通，会全部跳过）
        List<Seed> shuffled = new ArrayList<>(SEEDS);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        for (Seed seed : shuffled) {
            if (saved >= target) break;
            try {
                String md = fetchMarkdown(seed.url);
                if (md == null || md.isBlank()) continue;
                ParsedDoc doc = parseMarkdown(md, seed.url, seed.sourceName);
                if (doc == null) continue;
                if (!passTopicFilter(doc)) continue;
                if (isDuplicate(doc.title, seed.url)) continue;

                if (saveArticle(doc, seed.sourceName, seed.url, ThreadLocalRandom.current().nextInt(0, 60))) {
                    saved++;
                }
            } catch (Exception e) {
                log.warn("CrawlFail (jina) url={} err={}", seed.url, e.getMessage());
            }
        }

        // Phase 2：剩余额度用 AI 生成
        if (saved < target) {
            log.info("CrawlPhase AI gen start. need={}", target - saved);
            List<String> topics = new ArrayList<>(GENERATION_TOPICS);
            Collections.shuffle(topics, ThreadLocalRandom.current());
            for (String topic : topics) {
                if (saved >= target) break;
                try {
                    ParsedDoc doc = aiGenerateArticle(topic);
                    if (doc == null) continue;
                    if (isDuplicate(doc.title, "ai:" + topic)) continue;

                    if (saveArticle(doc, "MindMan AI 实验室", "ai://generated/" + System.currentTimeMillis(),
                            ThreadLocalRandom.current().nextInt(0, 60))) {
                        saved++;
                    }
                } catch (Exception e) {
                    log.warn("CrawlFail (ai) topic={} err={}", topic, e.getMessage());
                }
            }
        }

        log.info("crawlOnce done saved={} target={}", saved, target);
        return saved;
    }

    @Override
    public List<String> listSeeds() {
        List<String> all = new ArrayList<>();
        SEEDS.forEach(s -> all.add("[外部源] " + s.sourceName + ": " + s.url));
        all.add("[AI 主题池] size=" + GENERATION_TOPICS.size());
        return all;
    }

    /** Jina Reader 取 Markdown */
    private String fetchMarkdown(String url) {
        try {
            return webClientBuilder.build()
                    .get()
                    .uri("https://r.jina.ai/" + url)
                    .header(HttpHeaders.USER_AGENT, "MindMan-Crawler/1.0")
                    .header("X-Return-Format", "markdown")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(8))
                    .onErrorResume(e -> Mono.empty())
                    .block();
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 Markdown：第 1 行 title，正文前 1800 字 */
    private ParsedDoc parseMarkdown(String md, String url, String sourceName) {
        try {
            String[] lines = md.split("\\R");
            String title = "未命名";
            int start = 0;
            for (int i = 0; i < Math.min(lines.length, 30); i++) {
                String l = lines[i].trim();
                if (l.startsWith("# ")) {
                    title = l.substring(2).trim();
                    start = i + 1;
                    break;
                }
            }
            final String finalTitle = title;

            StringBuilder body = new StringBuilder();
            int charBudget = 1800;
            for (int i = start; i < lines.length && body.length() < charBudget; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("![")) continue;
                body.append(line).append("\n");
            }
            String content = body.toString().trim();
            if (content.length() < 50) return null;

            final String finalContent = content;
            String summary = content.length() > 120 ? content.substring(0, 120) + "..." : content;
            String tags = TOPIC_KEYWORDS.stream()
                    .filter(kw -> finalContent.contains(kw) || finalTitle.contains(kw))
                    .limit(4)
                    .collect(Collectors.joining(","));
            return new ParsedDoc(finalTitle, summary, content, tags);
        } catch (Exception e) {
            return null;
        }
    }

    /** 关键词过滤 */
    private boolean passTopicFilter(ParsedDoc d) {
        int hits = 0;
        for (String kw : TOPIC_KEYWORDS) {
            if (d.title.contains(kw) || d.content.contains(kw)) hits++;
            if (hits >= 2) return true;
        }
        return false;
    }

    /** AI 生成：调 Qwen 出原始内容 */
    private ParsedDoc aiGenerateArticle(String topic) {
        if (aiWebClient == null) {
            log.warn("aiWebClient not available, skipping AI gen");
            return null;
        }
        String sysPrompt = """
                你是一位擅长科普与情感陪伴的心理学科普博主。给定主题，请按以下要求写一篇原创心理学科普文章：
                1. 标题简洁、有趣，20 字以内
                2. 内容 700–1100 字，分 3–5 个小节，每节有小标题
                3. 面向都市年轻读者（20–40 岁）
                4. 用 markdown 写，每节用 `###` 开头
                5. 内容应包含至少 2 个具体可执行的小练习或自处方法
                6. 不要承诺治愈、不要做诊断，只提供理解与思路
                7. 不要在文中重复主题词做开篇
                """;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "qwen-plus"); // 用最快最便宜的兜底模型
        body.put("max_tokens", 1200);
        body.put("temperature", 0.85);
        body.put("messages", List.of(
                Map.of("role", "system", "content", sysPrompt),
                Map.of("role", "user",   "content", "主题：" + topic)
        ));

        try {
            String response = aiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .onErrorResume(e -> {
                        log.warn("AI gen fail topic={} err={}", topic, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
            if (response == null || response.isBlank()) return null;

            // 简单提取 content（JSON 解析：取首条 choices.message.content）
            int idx = response.indexOf("\"content\":");
            if (idx < 0) return null;
            int open = response.indexOf("\"", idx + 11);
            int close = response.indexOf("\"", open + 1);
            // 大概率需要 escapes；这里只取一段保守长度
            String content = decodeEscapesSafely(extractJsonString(response, idx));

            if (content == null || content.length() < 80) return null;

            // 抽 title
            String title = topic;
            for (String line : content.split("\\R")) {
                String l = line.trim();
                if (l.startsWith("# ") || l.startsWith("## ")) {
                    title = l.replaceFirst("^#+\\s*", "").trim();
                    break;
                }
            }
            String summary = content.length() > 140 ? content.substring(0, 140) + "..." : content;
            final String finalContent2 = content;
            final String finalTitle2 = title;
            String tags = TOPIC_KEYWORDS.stream()
                    .filter(kw -> finalContent2.contains(kw) || finalTitle2.contains(kw))
                    .limit(4)
                    .collect(Collectors.joining(","));
            return new ParsedDoc(title, summary, content, tags);
        } catch (Exception e) {
            log.warn("AI gen exception topic={} err={}", topic, e.getMessage());
            return null;
        }
    }

    /** 在 JSON 字符串里找完整 "..." 段（容忍含 `\"` 转义） */
    private String extractJsonString(String raw, int startIdx) {
        // 从 "content":"..." 的开引号往后找匹配的闭引号
        int openQuote = raw.indexOf('"', startIdx + 10);
        if (openQuote < 0) return null;
        StringBuilder sb = new StringBuilder();
        int i = openQuote + 1;
        boolean escape = false;
        for (; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escape) { sb.append('\\').append(c); escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') break;
            sb.append(c);
        }
        return decodeEscapesSafely(sb.toString());
    }

    /** 将 \n \t \" \\ 字符反转义（保留中文 UTF-8） */
    private String decodeEscapesSafely(String s) {
        if (s == null) return null;
        return s.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /** 重复检查：按 URL/标题去重 */
    private boolean isDuplicate(String title, String url) {
        Long byUrl = articleMapper.selectCount(new QueryWrapper<Article>().eq("source_url", url));
        if (byUrl != null && byUrl > 0) return true;
        Long byTitle = articleMapper.selectCount(new QueryWrapper<Article>().eq("title", title));
        return byTitle != null && byTitle > 0;
    }

    private boolean saveArticle(ParsedDoc d, String sourceName, String sourceUrl, int minutesAgo) {
        Article a = new Article();
        a.setCategoryId(CATEGORY_LIVE);
        a.setTitle(d.title);
        a.setSummary(d.summary);
        a.setContent(d.content);
        a.setAuthor(sourceName);
        a.setTags(d.tags);
        a.setReads(0L);
        a.setStatus(1);
        a.setPublishTime(LocalDateTime.now().minusMinutes(minutesAgo));
        a.setSourceType("crawled");
        a.setSourceUrl(sourceUrl);
        a.setSourceName(sourceName);
        a.setEmotionTags(d.tags);
        try {
            articleMapper.insert(a);
            log.info("CrawlSaved id={} title={} src={}", a.getId(), a.getTitle(), sourceName);
            return true;
        } catch (Exception e) {
            log.warn("saveArticle fail title={} err={}", d.title, e.getMessage());
            return false;
        }
    }

    private record Seed(String sourceName, String url) {}

    private static final class ParsedDoc {
        final String title;
        final String summary;
        final String content;
        final String tags;
        ParsedDoc(String t, String s, String c, String tags) {
            this.title = t; this.summary = s; this.content = c; this.tags = tags;
        }
    }
}
