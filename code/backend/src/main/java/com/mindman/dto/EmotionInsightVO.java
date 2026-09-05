package com.mindman.dto;

import lombok.Data;

import java.util.List;

/**
 * 本周情绪洞察 VO。
 *
 * <p>前端 AI 本周洞察卡片使用。</p>
 */
@Data
public class EmotionInsightVO {

    /** 主推文（基于真实数据生成的总结） */
    private String summary;

    /** 高亮标签（3 个短指标） */
    private List<String> highlights;

    /** 本周记录天数（distinct 日期） */
    private Integer recordDays;

    /** 上周记录天数 */
    private Integer prevRecordDays;

    /** 本周焦虑天数 */
    private Integer anxietyDays;

    /** 上周焦虑天数 */
    private Integer prevAnxietyDays;

    /** 本周情绪评分均值 */
    private Double avgScore;

    /** 本周情绪评分峰值 */
    private Double peakScore;

    /** 主导情绪 */
    private String dominantEmotion;

    /** 近 7 天每日均分（不足 7 天补 null），用于 sparkline */
    private List<Double> trendValues;
}
