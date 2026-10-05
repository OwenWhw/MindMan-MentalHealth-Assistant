package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindman.common.enums.MessageRoleEnum;
import com.mindman.common.exception.NotFoundException;
import com.mindman.common.page.PageVO;
import com.mindman.ai.MindManAgent;
import com.mindman.dto.AdminSessionVO;
import com.mindman.dto.ChatMessageVO;
import com.mindman.dto.ChatSendDTO;
import com.mindman.dto.ChatSessionCreateDTO;
import com.mindman.dto.ChatSessionVO;
import com.mindman.entity.ChatMessage;
import com.mindman.entity.ChatSession;
import com.mindman.entity.User;
import com.mindman.mapper.ChatMessageMapper;
import com.mindman.mapper.ChatSessionMapper;
import com.mindman.mapper.UserMapper;
import com.mindman.service.ChatService;
import com.mindman.util.EmotionAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 心理咨询 AI 对话服务实现。
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>用户消息即时入库，AI 回复在生成后入库（含情绪分析标签）</li>
 *   <li>会话标题：若创建时未指定，则默认为"新的咨询"；首条消息发送时自动截取生成</li>
 *   <li>会话列表附带最近消息预览和消息总数，方便前端展示</li>
 *   <li>AI 回复和个人资料分析通过 {@link MindManAgent} 协调</li>
 *   <li>流式模式下，AI 消息在流结束后统一入库；同步模式下即时入库</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final UserMapper userMapper;
    private final MindManAgent mindManAgent;

    // ======================== 会话管理 ========================

    @Override
    @Transactional
    public ChatSessionVO createSession(Long userId, ChatSessionCreateDTO dto) {
        // 参数校验：标题长度限制
        String title = dto.getTitle();
        if (title != null && !title.isBlank()) {
            title = title.trim();
            if (title.length() > 50) {
                title = title.substring(0, 50);
            }
        } else {
            title = "新的咨询";
        }

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        log.info("用户 {} 创建会话 id={}, title={}", userId, session.getId(), title);
        return toSessionVO(session, null, 0);
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt)
        );

        return sessions.stream().map(session -> {
            Long count = messageMapper.selectCount(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, session.getId())
            );
            ChatMessage lastMsg = messageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, session.getId())
                            .orderByDesc(ChatMessage::getCreatedAt)
                            .orderByDesc(ChatMessage::getId)
                            .last("LIMIT 1")
            );
            String preview = lastMsg != null
                    ? truncate(lastMsg.getContent(), 30)
                    : "";
            return toSessionVO(session, preview, count.intValue());
        }).collect(Collectors.toList());
    }

    @Override
    public ChatSessionVO getSessionDetail(Long userId, Long sessionId) {
        ChatSession session = getOwnedSession(userId, sessionId);
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
        );
        return toSessionVO(session, null, count.intValue());
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        getOwnedSession(userId, sessionId);
        // 物理删除消息 + 会话（级联删除）
        messageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
        );
        sessionMapper.deleteById(sessionId);
        log.info("用户 {} 删除会话 id={}", userId, sessionId);
    }

    // ======================== 消息收发 ========================

    @Override
    public List<ChatMessageVO> sendMessage(Long userId, ChatSendDTO dto) {
        ChatSession session = getOwnedSession(userId, dto.getSessionId());
        ensureActive(session);

        List<ChatMessageVO> result = new ArrayList<>(2);

        // ── 1. 保存用户消息 ──
        String displayContent = dto.getDisplayContentOrContent().trim();
        ChatMessage userMsg = saveUserMessage(session.getId(), userId, displayContent);
        result.add(toMessageVO(userMsg));
        updateSessionAfterMessage(session, displayContent);

        // 即使模型调用失败，用户消息也已落库，并保留一条可恢复的失败状态记录。
        ChatMessage aiMsg = createPendingAiMessage(session.getId(), userId);
        try {
            String aiReply = mindManAgent.respondSync(userId, dto,
                    buildContext(session.getId(), userMsg.getId(), aiMsg.getId()));
            if (aiReply == null || aiReply.isBlank()) {
                throw new IllegalStateException("AI 没有生成有效回复，请重试");
            }
            finishAiMessage(aiMsg, aiReply, "complete", EmotionAnalyzer.analyze(displayContent));
        } catch (RuntimeException e) {
            finishAiMessage(aiMsg, "", "failed", null);
            throw e;
        }
        result.add(toMessageVO(aiMsg));
        return result;
    }

    @Override
    public List<ChatMessageVO> listMessages(Long userId, Long sessionId, int page, int size) {
        getOwnedSession(userId, sessionId);

        IPage<ChatMessage> paged = messageMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt)
                        .orderByAsc(ChatMessage::getId)
        );

        return paged.getRecords().stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());
    }

    @Override
    public ChatSession getSessionEntity(Long userId, Long sessionId) {
        return getOwnedSession(userId, sessionId);
    }

    @Override
    @Transactional
    public void touchSession(Long userId, Long sessionId, String userContent) {
        ChatSession s = getOwnedSession(userId, sessionId);
        ensureActive(s);
        updateSessionAfterMessage(s, userContent);
    }

    @Override
    @Transactional
    public void archiveSession(Long userId, Long sessionId) {
        ChatSession s = getOwnedSession(userId, sessionId);
        if (Integer.valueOf(2).equals(s.getStatus())) return;
        s.setStatus(2);
        s.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(s);
        log.info("用户 {} 归档会话 id={}", userId, sessionId);
    }

    @Override
    @Transactional
    public void restoreSession(Long userId, Long sessionId) {
        ChatSession s = getOwnedSession(userId, sessionId);
        if (!Integer.valueOf(2).equals(s.getStatus())) return;
        sessionMapper.update(null, new UpdateWrapper<ChatSession>()
                .eq("id", sessionId)
                .eq("user_id", userId)
                .set("status", 1)
                .set("updated_at", LocalDateTime.now()));
        log.info("用户 {} 恢复会话 id={}", userId, sessionId);
    }

    @Override
    @Transactional
    public void saveSessionSummary(Long userId, Long sessionId, String summary) {
        getOwnedSession(userId, sessionId);
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("会话总结不能为空");
        }
        sessionMapper.update(null, new UpdateWrapper<ChatSession>()
                .eq("id", sessionId)
                .eq("user_id", userId)
                .set("summary", summary.trim())
                .set("summary_updated_at", LocalDateTime.now()));
    }

    // ======================== 管理端 ========================

    @Override
    public PageVO<AdminSessionVO> adminPageSessions(int page, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            if (k.matches("\\d+")) {
                wrapper.eq(ChatSession::getId, Long.parseLong(k));
            } else {
                List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .like(User::getUsername, k).or().like(User::getNickname, k));
                List<Long> uids = users.stream().map(User::getId).collect(Collectors.toList());
                if (uids.isEmpty()) {
                    return PageVO.of(0, page, pageSize, List.of());
                }
                wrapper.in(ChatSession::getUserId, uids);
            }
        }
        if (status != null) {
            wrapper.eq(ChatSession::getStatus, status);
        }
        wrapper.orderByDesc(ChatSession::getUpdatedAt);

        IPage<ChatSession> paged = sessionMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Long> userIds = paged.getRecords().stream()
                .map(ChatSession::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<AdminSessionVO> list = paged.getRecords().stream()
                .map(s -> toAdminSessionVO(s, userMap.get(s.getUserId())))
                .collect(Collectors.toList());

        return PageVO.of(paged.getTotal(), page, pageSize, list);
    }

    @Override
    public List<ChatMessageVO> adminListMessages(Long sessionId) {
        List<ChatMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt)
        );
        return msgs.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void adminDeleteSession(Long sessionId) {
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
        log.info("管理端删除会话 id={}", sessionId);
    }

    private AdminSessionVO toAdminSessionVO(ChatSession s, User u) {
        Integer st = s.getStatus() == null ? 1 : s.getStatus();
        AdminSessionVO vo = new AdminSessionVO();
        vo.setSessionId(s.getId());
        vo.setUserId(s.getUserId());
        if (u != null) {
            vo.setUserName(u.getNickname() != null ? u.getNickname() : u.getUsername());
            vo.setAvatar(u.getAvatar());
        }
        vo.setStatus(st);
        vo.setStatusText(st == 2 ? "已归档" : "进行中");
        vo.setStartedAt(s.getCreatedAt());
        vo.setEndedAt(s.getUpdatedAt());

        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, s.getId()));
        vo.setMessageCount(count.intValue());

        ChatMessage last = messageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, s.getId())
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (last != null) {
            vo.setLastMessage(truncate(last.getContent(), 60));
            vo.setLastSender("user".equals(last.getRole()) ? "用户" : "AI");
            vo.setLastTime(last.getCreatedAt());
            vo.setEmotion(last.getEmotion());
        }
        return vo;
    }

    // ======================== 内部方法 ========================

    /**
     * 保存用户消息到数据库
     */
    private ChatMessage saveUserMessage(Long sessionId, Long userId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole(MessageRoleEnum.USER.getCode());
        msg.setContent(content.trim());
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        log.debug("用户消息已保存: sessionId={}, msgId={}", sessionId, msg.getId());
        return msg;
    }

    /**
     * 保存 AI 回复消息到数据库
     */
    private ChatMessage createPendingAiMessage(Long sessionId, Long userId) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole(MessageRoleEnum.ASSISTANT.getCode());
        msg.setContent("");
        msg.setDeliveryStatus("streaming");
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        log.debug("AI 回复占位记录已保存: sessionId={}, msgId={}", sessionId, msg.getId());
        return msg;
    }

    private void finishAiMessage(ChatMessage msg, String content, String deliveryStatus, String emotion) {
        msg.setContent(content == null ? "" : content);
        msg.setDeliveryStatus(deliveryStatus);
        msg.setEmotion(emotion);
        messageMapper.updateById(msg);
    }

    /**
     * 消息发送后更新会话信息：
     * <ul>
     *   <li>首次消息 → 自动从内容截取生成标题</li>
     *   <li>更新 updatedAt 时间戳</li>
     * </ul>
     */
    private void updateSessionAfterMessage(ChatSession session, String userContent) {
        // 首条消息自动生成标题
        String title = session.getTitle();
        boolean generateTitle = "新的咨询".equals(title) || "新的心理咨询".equals(title);
        if (generateTitle) {
            String autoTitle = truncate(userContent.trim(), 20);
            session.setTitle(autoTitle);
        }
        LocalDateTime now = LocalDateTime.now();
        session.setUpdatedAt(now);
        session.setSummary(null);
        session.setSummaryUpdatedAt(null);
        UpdateWrapper<ChatSession> update = new UpdateWrapper<ChatSession>()
                .eq("id", session.getId())
                .eq("user_id", session.getUserId())
                .set("updated_at", now)
                .set("summary", null)
                .set("summary_updated_at", null);
        if (generateTitle) update.set("title", session.getTitle());
        sessionMapper.update(null, update);
    }

    /**
     * 构建会话上下文（最近 N 条消息作为对话历史，传给 AI）
     */
    private String buildContext(Long sessionId, Long excludeMessageId, Long pendingAssistantId) {
        List<ChatMessage> recentMessages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .ne(excludeMessageId != null, ChatMessage::getId, excludeMessageId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 10")
        );

        if (recentMessages.isEmpty()) {
            return "";
        }

        // 倒序拼接为对话历史文本
        StringBuilder sb = new StringBuilder();
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ChatMessage m = recentMessages.get(i);
            if (pendingAssistantId != null && pendingAssistantId.equals(m.getId())) continue;
            if (MessageRoleEnum.ASSISTANT.getCode().equals(m.getRole())
                    && m.getDeliveryStatus() != null && !"complete".equals(m.getDeliveryStatus())) continue;
            String role = MessageRoleEnum.USER.getCode().equals(m.getRole()) ? "用户" : "AI";
            sb.append(role).append("：").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 校验会话归属（防止越权访问他人会话）
     */
    private ChatSession getOwnedSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new NotFoundException("会话不存在");
        }
        return session;
    }

    private void ensureActive(ChatSession session) {
        if (Integer.valueOf(2).equals(session.getStatus())) {
            throw new IllegalStateException("会话已归档，请先恢复后继续");
        }
    }

    /**
     * 情绪分析已统一使用 EmotionAnalyzer 工具类，不再需要此方法。
     */

    // ======================== VO 转换 ========================

    private ChatSessionVO toSessionVO(ChatSession session, String preview, Integer count) {
        Integer st = session.getStatus() == null ? 1 : session.getStatus();
        return ChatSessionVO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .status(st)
                .statusText(st == 2 ? "已归档" : "进行中")
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .lastMessagePreview(preview)
                .messageCount(count)
                .unreadCount(0)
                .build();
    }

    private ChatMessageVO toMessageVO(ChatMessage msg) {
        return ChatMessageVO.builder()
                .id(msg.getId())
                .sessionId(msg.getSessionId())
                .role(msg.getRole())
                .content(msg.getContent())
                .emotion(msg.getEmotion())
                .createdAt(msg.getCreatedAt())
                .deliveryStatus(msg.getDeliveryStatus() == null ? "complete" : msg.getDeliveryStatus())
                .build();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
