package com.mindman.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 情绪分析结果（用户端倾诉后分析）。
 *
 * <p>字段严格对齐前端 {@code ConsultView} 的展示：</p>
 * <ul>
 *   <li>{@code emotion / emotionIcon / emotionScore(0-100) / emotionStar(1-5)} 主导情绪与评分</li>
 *   <li>{@code stress / anxiety / sleepRisk}(0-100) 三维圆环 + 对应 {@code *Level} 文案</li>
 *   <li>{@code sleepStar / stressStar}(1-5) 星制评分</li>
 *   <li>{@code analyzedAt} 时间戳（yyyy-MM-dd HH:mm:ss）</li>
 *   <li>{@code suggestions} 建议列表</li>
 * </ul>
 */
@Data
public class EmotionAnalysisVO {

    private String emotion;        // 主导情绪（焦虑/低落/愤怒/疲惫/愉悦/平静）
    private String emotionIcon;    // emoji 图标
    private Integer emotionScore;  // 情绪评分 0-100（百分比）
    private Integer emotionStar;   // 情绪星制 1-5
    private Integer sleepStar;     // 睡眠质量星制 1-5
    private Integer stressStar;    // 压力水平星制 1-5
    private String analyzedAt;     // 分析时间 yyyy-MM-dd HH:mm:ss
    private Integer stress;        // 压力值 0-100
    private Integer anxiety;       // 焦虑指数 0-100
    private Integer sleepRisk;     // 睡眠风险 0-100
    private String stressLevel;    // 低 / 中 / 高
    private String anxietyLevel;
    private String sleepLevel;
    private List<String> suggestions;
}
