package com.mindman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindman.entity.EmotionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 情绪记录 Mapper
 */
@Mapper
public interface EmotionRecordMapper extends BaseMapper<EmotionRecord> {
}
