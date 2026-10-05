package com.mindman.service.impl;

import com.mindman.entity.EmotionRecord;
import com.mindman.mapper.EmotionRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmotionInsightServiceImplTest {

    @Test
    void weeklyScoreAndTrendIgnoreHistoricalRatingsWithoutSource() {
        EmotionRecordMapper mapper = mock(EmotionRecordMapper.class);
        EmotionRecord verified = new EmotionRecord();
        verified.setRecordDate(LocalDate.now());
        verified.setEmotion("平静");
        verified.setEmotionScore(5);
        verified.setRatingSource(EmotionRecord.RATING_SOURCE_SELF_REPORTED);

        EmotionRecord legacy = new EmotionRecord();
        legacy.setRecordDate(LocalDate.now());
        legacy.setEmotion("平静");
        legacy.setEmotionScore(1);
        legacy.setRatingSource(null);

        when(mapper.selectList(any())).thenReturn(List.of(verified, legacy)).thenReturn(List.of());

        var insight = new EmotionInsightServiceImpl(mapper).thisWeekInsight(42L);

        assertEquals(5.0, insight.getAvgScore());
        assertEquals(5.0, insight.getPeakScore());
        int todayIndex = LocalDate.now().getDayOfWeek().getValue() - 1;
        assertEquals(5.0, insight.getTrendValues().get(todayIndex));
    }
}
