package com.mindman.ai;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式对话客户端抽象。
 *
 * <p>统一封装"OpenAI 兼容 /chat/completions"协议的对话调用，屏蔽底层供应商差异
 * （阿里云百炼 / 硅基流动 / Ollama 本地模型均兼容该协议）。
 *
 * <h3>实现原理</h3>
 * <ul>
 *   <li><b>流式</b>：{@code stream=true}，服务端返回 SSE 数据流，逐段解析 {@code delta.content}</li>
 *   <li><b>同步</b>：{@code stream=false}，一次性返回完整 JSON</li>
 * </ul>
 */
public interface StreamingChatClient {

    /** 通道名（cloud / ollama），用于日志与诊断接口 */
    String name();

    /** 当前通道是否可用（云端看 api-key 是否配置；Ollama 看是否启用） */
    boolean isAvailable();

    /**
     * 流式对话：返回逐段文本增量（已过滤 &lt;think&gt; 推理段）。
     *
     * @param messages 完整消息列表（含 system）
     * @param options  采样参数，null 则用客户端默认值
     */
    Flux<String> stream(List<ChatMessage> messages, ChatOptions options);

    /**
     * 同步对话：阻塞直至返回完整回复文本。
     */
    String call(List<ChatMessage> messages, ChatOptions options);
}
