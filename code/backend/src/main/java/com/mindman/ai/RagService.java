package com.mindman.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG（检索增强生成）服务。
 *
 * <p>流程：用户问题 → 向量检索 Top-K 相关文章片段 → 拼装为知识库上下文，
 * 由 {@code AiChatServiceImpl} 注入对话消息列表，让 AI 回答有据可依。
 *
 * <p>任何环节失败（Ollama 未启动 / 未建索引 / 检索为空）都返回 null，
 * 对话侧静默降级为普通模式，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final RagProperties props;
    private final VectorStoreService vectorStore;

    /**
     * 构建知识库上下文；不可用时返回 null。
     */
    public String buildContext(String question) {
        if (!props.isEnabled()) return null;
        try {
            List<VectorStoreService.SearchHit> hits = vectorStore.search(question, props.getTopK());
            if (hits.isEmpty()) return null;

            StringBuilder sb = new StringBuilder("以下是知识库中与用户问题可能相关的心理科普资料片段，回答时可以参考，但不要逐字照搬，也不要提及'资料'二字：\n\n");
            for (int i = 0; i < hits.size(); i++) {
                VectorStoreService.SearchHit h = hits.get(i);
                sb.append("【资料").append(i + 1).append("·").append(h.articleTitle()).append("】\n")
                  .append(h.content()).append("\n\n");
            }
            log.info("[RAG] 命中 {} 个片段: {}", hits.size(),
                    hits.stream().map(h -> h.articleTitle() + "(" + String.format("%.2f", h.score()) + ")").toList());
            return sb.toString();
        } catch (Exception e) {
            log.warn("[RAG] 构建上下文失败，降级为普通对话: {}", e.getMessage());
            return null;
        }
    }

    /** RAG 状态（管理端诊断用） */
    public java.util.Map<String, Object> status() {
        return java.util.Map.of(
                "enabled", props.isEnabled(),
                "embeddingModel", props.getEmbeddingModel(),
                "topK", props.getTopK(),
                "minScore", props.getMinScore(),
                "indexedChunks", props.isEnabled() ? vectorStore.size() : 0);
    }
}
