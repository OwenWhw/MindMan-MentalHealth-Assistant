package com.mindman.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.entity.Article;
import com.mindman.entity.ArticleEmbedding;
import com.mindman.mapper.ArticleEmbeddingMapper;
import com.mindman.mapper.ArticleMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轻量向量存储：MySQL 持久化 + 内存缓存 + 余弦相似度 Top-K 检索。
 *
 * <h3>设计取舍</h3>
 * <ul>
 *   <li>不引入 Milvus/RedisStack 等独立向量库——心理科普文章量级（数百~数千块）
 *       在 JVM 内做余弦检索是毫秒级，收益/复杂度比更高</li>
 *   <li>启动时异步预热缓存；管理端重建索引后刷新</li>
 *   <li>模型名不一致的旧向量在重建前被忽略（避免维度/语义空间混用）</li>
 * </ul>
 */
@Slf4j
@Service
public class VectorStoreService {

    private final ArticleEmbeddingMapper embeddingMapper;
    private final ArticleMapper articleMapper;
    private final EmbeddingClient embeddingClient;
    private final RagProperties ragProps;
    private final ObjectMapper objectMapper;

    /** 内存缓存：embeddingId → 向量缓存行 */
    private final Map<Long, CachedVector> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    /** 一条缓存向量 */
    public record CachedVector(Long articleId, String content, float[] vector) {}

    /** 检索结果：带文章标题与相似度 */
    public record SearchHit(Long articleId, String articleTitle, String content, double score) {}

    public VectorStoreService(ArticleEmbeddingMapper embeddingMapper, ArticleMapper articleMapper,
                              EmbeddingClient embeddingClient, RagProperties ragProps,
                              ObjectMapper objectMapper) {
        this.embeddingMapper = embeddingMapper;
        this.articleMapper = articleMapper;
        this.embeddingClient = embeddingClient;
        this.ragProps = ragProps;
        this.objectMapper = objectMapper;
    }

    // ======================== 索引构建 ========================

    /**
     * 重建全部文章索引：文章按段落切块 → 批量嵌入 → 整表替换。
     *
     * @return 索引的块数
     */
    public synchronized int reindexAll() {
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1));
        int total = 0;
        // 整表清空后重建
        embeddingMapper.delete(new LambdaQueryWrapper<>());
        for (Article a : articles) {
            List<String> chunks = chunkText(a.getContent() == null ? a.getSummary() : a.getContent());
            if (chunks.isEmpty()) continue;
            try {
                List<float[]> vectors = embeddingClient.embedBatch(chunks);
                for (int i = 0; i < chunks.size(); i++) {
                    ArticleEmbedding e = new ArticleEmbedding();
                    e.setArticleId(a.getId());
                    e.setChunkIndex(i);
                    e.setContent(chunks.get(i));
                    e.setEmbedding(toJson(vectors.get(i)));
                    e.setModel(ragProps.getEmbeddingModel());
                    embeddingMapper.insert(e);
                    total++;
                }
            } catch (Exception e) {
                log.warn("[VectorStore] 文章 {} 嵌入失败，跳过: {}", a.getId(), e.getMessage());
            }
        }
        loaded.set(false); // 触发下次查询重新加载缓存
        log.info("[VectorStore] 索引重建完成：{} 篇文章，{} 个文本块", articles.size(), total);
        return total;
    }

    /** 按段落边界切分为 ≤ chunkSize 的块 */
    List<String> chunkText(String content) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) return chunks;
        for (String para : content.split("\n+")) {
            String p = para.trim();
            if (p.isEmpty()) continue;
            int size = ragProps.getChunkSize();
            for (int i = 0; i < p.length(); i += size) {
                chunks.add(p.substring(i, Math.min(p.length(), i + size)));
            }
        }
        return chunks;
    }

    // ======================== 检索 ========================

    /**
     * 语义检索：问题嵌入 → 与全部缓存向量算余弦 → Top-K（低于阈值剔除）。
     * 存储不可用（Ollama 未启动/未索引）时返回空列表，不抛异常。
     */
    public List<SearchHit> search(String question, int topK) {
        ensureLoaded();
        if (cache.isEmpty()) return List.of();
        float[] qv;
        try {
            qv = embeddingClient.embed(question);
        } catch (Exception e) {
            log.warn("[VectorStore] 问题嵌入失败，RAG 跳过: {}", e.getMessage());
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        Map<Long, String> titles = loadTitles();
        for (CachedVector cv : cache.values()) {
            double score = cosine(qv, cv.vector());
            if (score >= ragProps.getMinScore()) {
                hits.add(new SearchHit(cv.articleId(),
                        titles.getOrDefault(cv.articleId(), "未知文章"), cv.content(), score));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(topK)
                .toList();
    }

    /** 索引块总数 */
    public int size() {
        ensureLoaded();
        return cache.size();
    }

    // ======================== 内部方法 ========================

    private void ensureLoaded() {
        if (loaded.get()) return;
        synchronized (this) {
            if (loaded.get()) return;
            cache.clear();
            for (ArticleEmbedding e : embeddingMapper.selectList(new LambdaQueryWrapper<>())) {
                if (!ragProps.getEmbeddingModel().equals(e.getModel())) continue; // 模型不一致的旧向量跳过
                try {
                    cache.put(e.getId(), new CachedVector(e.getArticleId(), e.getContent(), fromJson(e.getEmbedding())));
                } catch (Exception ignored) {}
            }
            loaded.set(true);
            log.info("[VectorStore] 缓存加载完成：{} 个向量", cache.size());
        }
    }

    private Map<Long, String> loadTitles() {
        Map<Long, String> m = new ConcurrentHashMap<>();
        for (Article a : articleMapper.selectList(new LambdaQueryWrapper<Article>().select(Article::getId, Article::getTitle))) {
            m.put(a.getId(), a.getTitle());
        }
        return m;
    }

    /** 余弦相似度（向量假设已归一化与否均可，此处完整计算） */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String toJson(float[] v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private float[] fromJson(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
