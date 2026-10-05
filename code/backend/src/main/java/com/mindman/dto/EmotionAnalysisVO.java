package com.mindman.dto;

import lombok.Data;

import java.util.List;

/**
 * Current-turn emotion observations. Numeric legacy fields remain nullable for wire compatibility;
 * free-form conversation analysis is not a validated questionnaire and must not invent scores.
 */
@Data
public class EmotionAnalysisVO {

    private String emotion;        // 本轮文字中的情绪线索；证据不足时为“暂不判断”
    private String analysisSource; // agent / rules
    private String evidence;       // 原文中可核验的短句
    private String interpretation; // 基于已核验原话生成的克制反馈
    private List<EmotionCueVO> cues;
    private String analyzedAt;     // 分析时间 yyyy-MM-dd HH:mm:ss

    /** Deprecated numerical estimates are kept nullable so older clients can deserialize responses. */
    private Integer emotionScore;
    private Integer emotionStar;
    private Integer sleepStar;
    private Integer stressStar;
    private Integer stress;
    private Integer anxiety;
    private Integer sleepRisk;
    private String stressLevel;
    private String anxietyLevel;
    private String sleepLevel;
    private List<String> suggestions;
}
