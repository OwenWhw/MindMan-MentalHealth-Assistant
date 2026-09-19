package com.mindman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindman.entity.ArticleEmbedding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章嵌入向量 Mapper
 */
@Mapper
public interface ArticleEmbeddingMapper extends BaseMapper<ArticleEmbedding> {
}
