package com.mindman.service.impl;

import com.mindman.dto.EmotionRecordSaveDTO;
import com.mindman.entity.EmotionRecord;
import com.mindman.mapper.EmotionRecordMapper;
import com.mindman.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class EmotionRecordServiceImplTest {

    private final EmotionRecordMapper emotionRecordMapper = mock(EmotionRecordMapper.class);
    private final EmotionRecordServiceImpl service = new EmotionRecordServiceImpl(
            emotionRecordMapper, mock(UserMapper.class));

    @Test
    void plantMarksRatingsAsSelfReportedAndReturnsTheirProvenance() {
        when(emotionRecordMapper.insert(any(EmotionRecord.class))).thenAnswer(invocation -> {
            EmotionRecord record = invocation.getArgument(0);
            record.setId(7L);
            return 1;
        });
        EmotionRecordSaveDTO request = new EmotionRecordSaveDTO();
        request.setEmotion("平静");
        request.setContent("今天狀態平穩");
        request.setEmotionScore(4);
        request.setSleepScore(4);
        request.setStressScore(2);

        var result = service.plant(42L, request);

        ArgumentCaptor<EmotionRecord> captor = ArgumentCaptor.forClass(EmotionRecord.class);
        verify(emotionRecordMapper).insert(captor.capture());
        assertEquals(EmotionRecord.RATING_SOURCE_SELF_REPORTED, captor.getValue().getRatingSource());
        assertEquals(EmotionRecord.RATING_SOURCE_SELF_REPORTED, result.getRatingSource());
    }
}
