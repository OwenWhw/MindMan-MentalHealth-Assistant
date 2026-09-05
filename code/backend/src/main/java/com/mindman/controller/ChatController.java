package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.config.AiConfig;
import com.mindman.dto.ChatMessageVO;
import com.mindman.dto.ChatSendDTO;
import com.mindman.dto.ChatSessionCreateDTO;
import com.mindman.dto.ChatSessionVO;
import com.mindman.entity.ChatMessage;
import com.mindman.entity.ChatSession;
import com.mindman.mapper.ChatMessageMapper;
import com.mindman.service.AiChatService;
import com.mindman.service.ChatService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 心理咨询 AI 对话控制器。
 *
 * <h3>接口列表</h3>
 * <pre>
 * POST   /api/chat/sessions                创建会话
 * GET    /api/chat/sessions                我的会话列表
 * GET    /api/chat/sessions/{id}           会话详情
 * DELETE /api/chat/sessions/{id}           删除会话
 * POST   /api/chat/messages                发送消息（同步，含AI回复）
 * GET    /api/chat/sessions/{id}/messages  历史消息（分页）
 * POST   /api/chat/stream                  发送消息（SSE 流式输出 AI 回复） ⭐
 * </pre>
 *
 * <h3>SSE 流式接口说明</h3>
 * <p>{@code POST /api/chat/stream} 是核心流式接口：</p>
 * <ul>
 *   <li>请求体：{@code {"sessionId": 123, "content": "用户消息"}}</li>
 *   <li>响应：{@code text/event-stream}，每个事件包含一个文本片段</li>
 *   <li>SSE 事件格式：{@code data: {"text":"片段内容","done":false}}</li>
 *   <li>结束时发送：{@code data: {"text":"","done":true,"emotion":"焦虑"}}</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "心理咨询AI对话", description = "会话管理 + 消息收发 + AI 回复（支持SSE流式输出）")
public class ChatController {

    private final ChatService chatService;
    private final AiChatService aiChatService;
    private final ChatMessageMapper messageMapper;

    /** 异步线程池，用于 SSE 流式推送（不阻塞 Servlet 线程） */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chat-sse-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });

    // ======================== 会话 CRUD ========================

    @PostMapping("/sessions")
    @Operation(summary = "创建咨询会话")
    public R<ChatSessionVO> createSession(@Valid @RequestBody ChatSessionCreateDTO dto) {
        return R.ok(chatService.createSession(LoginUser.get(), dto));
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取我的会话列表")
    public R<List<ChatSessionVO>> listSessions() {
        return R.ok(chatService.listSessions(LoginUser.get()));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "获取会话详情")
    public R<ChatSessionVO> getSession(@PathVariable Long id) {
        return R.ok(chatService.getSessionDetail(LoginUser.get(), id));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "删除会话")
    public R<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(LoginUser.get(), id);
        return R.ok();
    }

    @PutMapping("/sessions/{id}/archive")
    @Operation(summary = "归档（结束）会话")
    public R<Void> archiveSession(@PathVariable Long id) {
        chatService.archiveSession(LoginUser.get(), id);
        return R.ok();
    }

    // ======================== 消息接口 ========================

    @PostMapping("/messages")
    @Operation(summary = "发送消息并获得AI回复（同步模式）")
    public R<List<ChatMessageVO>> sendMessage(@Valid @RequestBody ChatSendDTO dto) {
        return R.ok(chatService.sendMessage(LoginUser.get(), dto));
    }

    @GetMapping("/sessions/{id}/messages")
    @Operation(summary = "获取会话历史消息")
    public R<List<ChatMessageVO>> listMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(chatService.listMessages(LoginUser.get(), id, page, size));
    }

    // ======================== SSE 流式接口 ⭐ ========================

    /**
     * SSE 流式对话接口。
     *
     * <p>前端通过 fetch/EventSource 消费此接口，实现逐字打字机效果。</p>
     *
     * <h3>流程</h3>
     * <ol>
     *   <li>校验会话归属，保存用户消息到数据库</li>
     *   <li>创建 {@link SseEmitter}（超时 120 秒）</li>
     *   <li>异步调用 {@link AiChatService#chatStream} 获取 Flux 流</li>
     *   <li>将每个文本片段通过 SseEmitter 推送给前端</li>
     *   <li>流结束后保存完整 AI 回复到数据库，发送 done 事件</li>
     * </ol>
     *
     * <h3>SSE 事件数据格式</h3>
     * <pre>
     * data: {"text":"我听到了","done":false}
     * data: {"text":"你的分享","done":false}
     * data: {"text":"","done":true,"emotion":"焦虑"}
     * </pre>
     *
     * @param dto 发送请求（sessionId + content）
     * @return SseEmitter（Spring MVC 自动处理 text/event-stream 响应）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE流式对话（推荐）", description = "流式输出AI回复，支持打字机效果")
    public SseEmitter streamMessage(@Valid @RequestBody ChatSendDTO dto) {
        Long userId = LoginUser.get();

        // SseEmitter 超时时间 120 秒（足够长对话生成）
        // 超时后前端应自动重连或提示用户
        SseEmitter emitter = new SseEmitter(120_000L);

        // 异步执行，不阻塞 Tomcat IO 线程
        sseExecutor.execute(() -> {
            try {
                // 1. 校验会话归属
                ChatSession session = chatService.getSessionEntity(userId, dto.getSessionId());
                if (session == null) {
                    sendError(emitter, "会话不存在");
                    return;
                }

                // 2. 保存用户消息
                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(dto.getSessionId());
                userMsg.setUserId(userId);
                userMsg.setContent(dto.getContent().trim());
                userMsg.setRole("user");
                userMsg.setCreatedAt(LocalDateTime.now());
                messageMapper.insert(userMsg);

                // 2.1 刷新会话：首条消息自动命名 + 更新 updatedAt，
                //     保证流式聊天后会话列表仍按最近使用排序（否则切页后可能恢复错会话）
                chatService.touchSession(userId, dto.getSessionId(), dto.getContent());

                // 3. 构建上下文并调用 AI 流式服务
                String context = buildContext(dto.getSessionId());

                StringBuilder fullReply = new StringBuilder();
                aiChatService.chatStream(dto.getContent(), context, dto.getModel())
                        .doOnNext(chunk -> {
                            // 4. 逐段推送到前端
                            fullReply.append(chunk);
                            sendChunk(emitter, chunk, false);
                        })
                        .doOnComplete(() -> {
                            // 5. 流结束：保存 AI 消息到库 + 发送 done 事件
                            saveAiMessage(dto.getSessionId(), userId, fullReply.toString(), dto.getContent());
                            sendDone(emitter, fullReply.toString(), analyzeEmotion(dto.getContent()));
                            log.info("SSE 流式输出完成: sessionId={}, replyLen={}", dto.getSessionId(), fullReply.length());
                        })
                        .doOnError(e -> {
                            log.error("SSE 流式输出异常: {}", e.getMessage());
                            // 如果已产生部分回复也保存
                            if (fullReply.length() > 0) {
                                saveAiMessage(dto.getSessionId(), userId, fullReply.toString(), dto.getContent());
                            }
                            sendError(emitter, "AI 服务暂时不可用，请稍后重试");
                        })
                        .blockLast();  // 阻塞等待流完成（已在独立线程中）

            } catch (Exception e) {
                log.error("SSE 处理异常: {}", e.getMessage(), e);
                sendError(emitter, "服务器内部错误");
            }
        });

        // 超时 / 完成回调
        emitter.onTimeout(() -> log.warn("SSE 超时: sessionId={}", dto.getSessionId()));
        emitter.onCompletion(() -> log.debug("SSE 连接关闭: sessionId={}", dto.getSessionId()));

        return emitter;
    }

    // ======================== SSE 辅助方法 ========================

    /**
     * 发送文本片段事件
     *
     * @param emitter SseEmitter
     * @param text    文本片段
     * @param done    是否为最后一个事件
     */
    private void sendChunk(SseEmitter emitter, String text, boolean done) {
        try {
            String payload = "{\"text\":\"" + escapeJson(text) + "\",\"done\":" + done + "}";
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(payload));
        } catch (IOException e) {
            log.warn("SSE 发送片段失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 发送完成事件（附带情绪分析结果）
     */
    private void sendDone(SseEmitter emitter, String fullText, String emotion) {
        try {
            String payload = "{\"text\":\"\",\"done\":true,\"emotion\":\"" + escapeJson(emotion) + "\"}";
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(payload));
            emitter.complete();
        } catch (IOException e) {
            log.warn("SSE 发送完成事件失败: {}", e.getMessage());
        }
    }

    /**
     * 发送错误事件
     */
    private void sendError(SseEmitter emitter, String error) {
        try {
            String payload = "{\"error\":\"" + escapeJson(error) + "\",\"done\":true}";
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(payload));
            emitter.completeWithError(new RuntimeException(error));
        } catch (IOException e) {
            log.warn("SSE 发送错误事件失败: {}", e.getMessage());
        }
    }

    // ======================== 内部方法 ========================

    /**
     * 构建会话上下文（最近10条消息作为对话历史）
     */
    private String buildContext(Long sessionId) {
        // 通过 Service 层获取会更规范，但 Controller 直接查也可以避免循环依赖
        List<ChatMessage> recentMessages = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .last("LIMIT 10")
        );

        if (recentMessages.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ChatMessage m = recentMessages.get(i);
            String role = "user".equals(m.getRole()) ? "用户" : "AI";
            sb.append(role).append("：").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 保存 AI 回复消息到数据库
     */
    private void saveAiMessage(Long sessionId, Long userId, String content, String userContent) {
        if (content == null || content.isBlank()) return;
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setEmotion(analyzeEmotion(userContent));
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /**
     * 关键词情绪分析（轻量级，与 ChatServiceImpl 保持一致）
     */
    private String analyzeEmotion(String content) {
        if (content == null) return "平静";
        String lower = content.toLowerCase();
        if (lower.contains("焦虑") || lower.contains("紧张") || lower.contains("担心")) return "焦虑";
        if (lower.contains("难过") || lower.contains("抑郁") || lower.contains("伤心")) return "低落";
        if (lower.contains("开心") || lower.contains("高兴")) return "愉悦";
        if (lower.contains("愤怒") || lower.contains("生气")) return "愤怒";
        if (lower.contains("失眠") || lower.contains("睡不着")) return "疲惫";
        return "平静";
    }

    /**
     * JSON 字符串转义（防止 SSE 数据中的引号等破坏 JSON 结构）
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    @GetMapping("/models")
    @Operation(summary = "可用模型列表")
    public R<java.util.Map<String, String>> models() {
        return R.ok(AiConfig.MODEL_OPTIONS);
    }
}
