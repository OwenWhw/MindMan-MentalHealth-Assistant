package com.mindman.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Ollama 嵌入客户端（OpenAI 兼容 /v1/embeddings 端点）。
 *
 * <p>将文本转为 float[] 向量，供向量检索使用。
 * 需先在本地执行：{@code ollama pull nomic-embed-text}
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final WebClient webClient;
    private final RagProperties ragProps;
    private final ObjectMapper objectMapper;

    public EmbeddingClient(OllamaProperties ollamaProps, RagProperties ragProps, ObjectMapper objectMapper) {
        this.ragProps = ragProps;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        this.webClient = WebClient.builder()
                .baseUrl(ollamaProps.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** 单条文本嵌入 */
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量嵌入（OpenAI 兼容格式：{"model":..,"input":[..]} → data[i].embedding）
     */
    @SuppressWarnings("unchecked")
    public List<float[]> embedBatch(List<String> texts) {
        String resp = webClient.post()
                .uri("/embeddings")
                .body(BodyInserters.fromValue(Map.of(
                        "model", ragProps.getEmbeddingModel(),
                        "input", texts)))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(120));
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("嵌入响应缺少 data 数组");
            }
            java.util.List<float[]> out = new java.util.ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode emb = item.path("embedding");
                float[] v = new float[emb.size()];
                for (int i = 0; i < v.length; i++) v[i] = (float) emb.get(i).asDouble();
                out.add(v);
            }
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析嵌入响应失败: " + e.getMessage(), e);
        }
    }
}
