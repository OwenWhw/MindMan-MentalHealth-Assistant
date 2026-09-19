package com.mindman.controller;

import com.mindman.ai.RagService;
import com.mindman.ai.VectorStoreService;
import com.mindman.common.R;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端 - RAG 知识库索引管理。
 *
 * <p>接口一览：
 * <ul>
 *   <li>GET  /api/admin/rag/status    索引状态（启用/模型/块数）</li>
 *   <li>POST /api/admin/rag/reindex   重建全部文章向量索引（Ollama 必须在线）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/rag")
@Tag(name = "RAG 知识库索引")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagService ragService;
    private final VectorStoreService vectorStore;

    @GetMapping("/status")
    @Operation(summary = "索引状态")
    public R<Map<String, Object>> status() {
        LoginUser.requireAdmin();
        return R.ok(ragService.status());
    }

    @PostMapping("/reindex")
    @Operation(summary = "重建全部文章向量索引", description = "对全部已发布文章重新分块并嵌入，耗时取决于文章量与 Ollama 速度")
    public R<Map<String, Object>> reindex() {
        LoginUser.requireAdmin();
        int chunks = vectorStore.reindexAll();
        Map<String, Object> data = new HashMap<>();
        data.put("indexedChunks", chunks);
        data.put("embeddingModel", ragService.status().get("embeddingModel"));
        return R.ok(data);
    }
}
