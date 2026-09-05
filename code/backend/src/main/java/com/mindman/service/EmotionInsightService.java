package com.mindman.service;

import com.mindman.dto.EmotionInsightVO;

/**
 * 本周情绪洞察服务。
 *
 * <p>从 {@code emotion_record} 拉过去 7 天的记录，统计：
 * 记录天数、焦虑天数、情绪均值/峰值、主导情绪、近 7 日每日均值，
 * 与上周对比生成 summary + highlights。</p>
 */
public interface EmotionInsightService {

    /**
     * 当前登录用户的本周情绪洞察。
     */
    EmotionInsightVO thisWeekInsight(Long userId);
}
