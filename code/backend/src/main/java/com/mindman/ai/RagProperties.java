package com.mindman.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG（检索增强生成）配置。
 *
 * <p>架构说明：不引入 Milvus 等独立向量数据库，采用
 * 「MySQL 存储嵌入向量 + JVM 内存缓存 + 余弦相似度 Top-K」的轻量方案，
 * 适配本机开发与低配 ECS 部署；文章量在数千篇以内性能足够。
 *
 * <p>嵌入模型使用 Ollama 本地服务（需先 {@code ollama pull nomic-embed-text}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    /** 是否启用 RAG（依赖 ai.ollama.enabled=true 的 Ollama 服务） */
    private boolean enabled = false;

    /** 嵌入模型（ollama pull 后的名称） */
    private String embeddingModel = "nomic-embed-text";

    /** 检索返回的 Top-K 片段数 */
    private int topK = 3;

    /** 余弦相似度阈值，低于该值的片段不进入上下文（0~1） */
    private double minScore = 0.35;

    /** 文章分块长度（字符），按段落边界切分 */
    private int chunkSize = 500;
}
