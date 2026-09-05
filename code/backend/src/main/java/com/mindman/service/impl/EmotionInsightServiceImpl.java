package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mindman.dto.EmotionInsightVO;
import com.mindman.entity.EmotionRecord;
import com.mindman.mapper.EmotionRecordMapper;
import com.mindman.service.EmotionInsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 本周洞察实现（基于 emotion_record 真实数据生成）。
 *
 * <p>不调用 AI，纯规则式总结与标签生成，避免额度消耗和延迟。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionInsightServiceImpl implements EmotionInsightService {

    /** 焦虑类情绪关键词 */
    private static final List<String> ANXIETY_KEYWORDS = List.of(
            "焦虑", "紧张", "压抑", "烦躁", "委屈", "愤怒"
    );

    private final EmotionRecordMapper emotionRecordMapper;

    @Override
    public EmotionInsightVO thisWeekInsight(Long userId) {
        EmotionInsightVO vo = new EmotionInsightVO();

        // 时间窗：以本周一为起点，到今天
        LocalDate today = LocalDate.now();
        LocalDate thisMon = today.with(DayOfWeek.MONDAY);
        if (thisMon.isAfter(today)) thisMon = thisMon.minusWeeks(1);
        LocalDate prevMon = thisMon.minusDays(7);
        LocalDate lastSunday = thisMon.minusDays(1);  // 上周周日（含）

        // 查询本周 + 上周数据
        List<EmotionRecord> thisWeek = safeSelect(userId, thisMon, today);
        List<EmotionRecord> prevWeek = safeSelect(userId, prevMon, lastSunday);

        // 1. 记录天数
        Set<LocalDate> thisDates = thisWeek.stream().map(EmotionRecord::getRecordDate)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<LocalDate> prevDates = prevWeek.stream().map(EmotionRecord::getRecordDate)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        vo.setRecordDays(thisDates.size());
        vo.setPrevRecordDays(prevDates.size());

        // 2. 焦虑天数
        vo.setAnxietyDays(countAnxietyDays(thisWeek));
        vo.setPrevAnxietyDays(countAnxietyDays(prevWeek));

        // 3. 情绪分均值 / 峰值
        List<Integer> scores = thisWeek.stream()
                .map(EmotionRecord::getEmotionScore).filter(Objects::nonNull).toList();
        if (!scores.isEmpty()) {
            double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            int peak = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
            vo.setAvgScore(round1(avg));
            vo.setPeakScore((double) peak);
        }

        // 4. 主导情绪
        Map<String, Long> emotionFreq = thisWeek.stream()
                .map(EmotionRecord::getEmotion)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        if (!emotionFreq.isEmpty()) {
            String top = emotionFreq.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).get().getKey();
            vo.setDominantEmotion(top);
        }

        // 5. 近 7 天每日均值（sparkline 用）
        List<Double> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = thisMon.plusDays(i);
            if (day.isAfter(today)) {
                trend.add(null);
                continue;
            }
            LocalDate finalDay = day;
            OptionalDouble avg = thisWeek.stream()
                    .filter(r -> Objects.equals(r.getRecordDate(), finalDay))
                    .map(EmotionRecord::getEmotionScore)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .average();
            trend.add(avg.isPresent() ? round1(avg.getAsDouble()) : null);
        }
        vo.setTrendValues(trend);

        // 6. summary + highlights 模板化生成
        generateSummaryAndHighlights(vo, thisDates.size());

        return vo;
    }

    private List<EmotionRecord> safeSelect(Long userId, LocalDate from, LocalDate to) {
        try {
            return emotionRecordMapper.selectList(
                    new QueryWrapper<EmotionRecord>()
                            .eq("user_id", userId)
                            .ge("record_date", from)
                            .le("record_date", to));
        } catch (Exception e) {
            log.warn("EmotionInsight safeSelect failed: {}", e.getMessage());
            return List.of();
        }
    }

    private int countAnxietyDays(List<EmotionRecord> records) {
        return (int) records.stream()
                .map(EmotionRecord::getEmotion)
                .filter(e -> e != null && !e.isBlank())
                .filter(e -> ANXIETY_KEYWORDS.stream().anyMatch(e::contains))
                .count();
    }

    private void generateSummaryAndHighlights(EmotionInsightVO vo, int days) {
        StringBuilder sb = new StringBuilder();
        List<String> highlights = new ArrayList<>();

        if (days == 0) {
            vo.setSummary("本周还没有心情记录，今天试着种一朵花，开启你的心理健康之旅吧 🌱");
            vo.setHighlights(List.of("本周记录 0 天", "建议每天记录", "保持觉察"));
            return;
        }

        sb.append("本周你记录了").append(days).append("次心情");
        if (vo.getDominantEmotion() != null) {
            sb.append("，整体以「").append(vo.getDominantEmotion()).append("」");
            if (vo.getPrevRecordDays() != null && days > vo.getPrevRecordDays()) {
                sb.append("为主，且记录天数比上周增加");
                highlights.add("记录天数 +" + (days - vo.getPrevRecordDays()));
                sb.append(days - vo.getPrevRecordDays()).append("天");
            } else if (vo.getPrevRecordDays() != null && days < vo.getPrevRecordDays()) {
                sb.append("，记录节奏放缓");
                highlights.add("记录天数 -" + (vo.getPrevRecordDays() - days));
            }
        }
        sb.append("。");

        // 焦虑趋势
        if (vo.getAnxietyDays() != null && vo.getPrevAnxietyDays() != null) {
            int d = vo.getPrevAnxietyDays() - vo.getAnxietyDays();
            if (d > 0) {
                sb.append("焦虑天数减少了").append(d).append("天。");
                int pct = vo.getPrevAnxietyDays() > 0
                        ? (int) Math.round(100.0 * d / vo.getPrevAnxietyDays()) : 100;
                highlights.add("焦虑降低 " + pct + "%");
            } else if (d < 0) {
                sb.append("焦虑天数上升").append(-d).append("天，请多关照自己 🫂。");
                int pct = vo.getPrevAnxietyDays() > 0
                        ? (int) Math.round(100.0 * (-d) / vo.getPrevAnxietyDays()) : 0;
                highlights.add("焦虑上升 " + pct + "%");
            } else {
                sb.append("焦虑状态与上周持平。");
                highlights.add("焦虑持平");
            }
        }

        // 峰值
        if (vo.getPeakScore() != null) {
            sb.append("本周情绪峰值 ").append(formatScore(vo.getPeakScore())).append(" 分");
            if (vo.getAvgScore() != null) {
                sb.append("，平均").append(formatScore(vo.getAvgScore())).append("分");
            }
            sb.append("。");
            highlights.add("情绪峰值 " + formatScore(vo.getPeakScore()));
        }

        // 建议
        if (vo.getAnxietyDays() != null && vo.getAnxietyDays() >= 3) {
            sb.append("焦虑偏频繁，建议每天 5 分钟冥想或呼吸练习。");
        } else if (vo.getAvgScore() != null && vo.getAvgScore() < 3.5) {
            sb.append("情绪分偏低，尝试与信任的人聊聊或写下来感受。");
        } else {
            sb.append("建议继续保持每日觉察 ❤️。");
        }

        vo.setSummary(sb.toString());
        vo.setHighlights(highlights);
    }

    private static double round1(double d) {
        return Math.round(d * 10.0) / 10.0;
    }

    private static String formatScore(Double d) {
        if (d == null) return "—";
        return d % 1 == 0 ? String.valueOf(d.intValue()) : String.valueOf(round1(d));
    }
}
