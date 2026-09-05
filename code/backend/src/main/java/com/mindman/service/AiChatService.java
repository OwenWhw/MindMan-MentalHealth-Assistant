package com.mindman.service;

import reactor.core.publisher.Flux;

/**
 * AI 聊天服务接口。
 *
 * <h3>能力说明</h3>
 * <ul>
 *   <li><b>同步模式</b>：发送消息，等待完整回复后返回（用于普通 sendMessage 接口）</li>
 *   <li><b>流式模式</b>：返回 Flux&lt;String&gt;，每个元素为 AI 回复的一个文本片段（用于 SSE 推送）</li>
 * </ul>
 *
 * <h3>接入平台</h3>
 * <p>默认使用<b>硅基流动(SiliconFlow)</b>平台，兼容 OpenAI API 格式。
 * 通过 {@code ai.siliconflow.*} 配置项指定 API Key、模型、端点。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 同步调用
 * String reply = aiChatService.chatSync("我最近很焦虑", context);
 *
 * // 流式调用
 * Flux&lt;String&gt; flux = aiChatService.chatStream("我最近很焦虑", context);
 * flux.subscribe(chunk -&gt; System.out.println("收到: " + chunk));
 * </pre>
 */
public interface AiChatService {

    /**
     * 同步调用 AI，获取完整回复文本。
     *
     * @param userMessage 用户当前输入
     * @param context     会话上下文（最近几轮对话历史，可为空）
     * @param model       模型名称（可选，传空则用默认模型）
     * @return AI 完整回复文本
     */
    String chatSync(String userMessage, String context, String model);

    /**
     * 流式调用 AI，返回文本片段流。
     *
     * @param userMessage 用户当前输入
     * @param context     会话上下文（最近几轮对话历史，可为空）
     * @param model       模型名称（可选，传空则用默认模型）
     * @return 文本片段 Flux 流
     */
    Flux<String> chatStream(String userMessage, String context, String model);
}
