package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章分块嵌入向量（RAG 检索用）。
 *
 * <p>每行 = 文章的一个文本块；embedding 为 JSON 数组字符串（float 序列）。
 * 检索时全量载入内存做余弦相似度——数千块以内毫秒级。
 */
@Data
@TableName("article_embedding")
public class ArticleEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文章 ID */
    private Long articleId;

    /** 块序号（0 起） */
    private Integer chunkIndex;

    /** 文本块内容 */
    private String content;

    /** 嵌入向量（JSON 数组字符串） */
    private String embedding;

    /** 嵌入模型名（便于换模型后重建索引） */
    private String model;

    private LocalDateTime createdAt;
}
