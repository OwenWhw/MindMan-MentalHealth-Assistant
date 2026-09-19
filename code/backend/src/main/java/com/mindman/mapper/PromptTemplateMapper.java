package com.mindman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindman.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板 Mapper
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
