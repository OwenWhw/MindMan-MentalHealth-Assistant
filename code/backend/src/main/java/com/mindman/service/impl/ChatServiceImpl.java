package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindman.common.enums.MessageRoleEnum;
import com.mindman.common.exception.NotFoundException;
import com.mindman.common.page.PageVO;
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
import com.mindman.service.AiChatService;
import com.mindman.service.ChatService;
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
 *   <li>AI 回复通过 {@link AiChatService} 实现，支持同步和流式两种模式</li>
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
    private final AiChatService aiChatService;

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
    @Transactional
    public List<ChatMessageVO> sendMessage(Long userId, ChatSendDTO dto) {
        ChatSession session = getOwnedSession(userId, dto.getSessionId());

        List<ChatMessageVO> result = new ArrayList<>(2);

        // ── 1. 保存用户消息 ──
        ChatMessage userMsg = saveUserMessage(session.getId(), userId, dto.getContent());
        result.add(toMessageVO(userMsg));

        // ── 2. 调用 AI 服务生成回复（同步模式）──
        String aiReply = aiChatService.chatSync(dto.getContent(), buildContext(session.getId()), dto.getModel());
        String emotion = analyzeEmotion(dto.getContent());

        // ── 3. 保存 AI 回复 ──
        ChatMessage aiMsg = saveAiMessage(session.getId(), userId, aiReply, emotion);
        result.add(toMessageVO(aiMsg));

        // ── 4. 更新会话元信息 ──
        updateSessionAfterMessage(session, dto.getContent());

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
        updateSessionAfterMessage(s, userContent);
    }

    @Override
    @Transactional
    public void archiveSession(Long userId, Long sessionId) {
        ChatSession s = getOwnedSession(userId, sessionId);
        s.setStatus(2);
        sessionMapper.updateById(s);
        log.info("用户 {} 归档会话 id={}", userId, sessionId);
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
        vo.setStatusText(st == 2 ? "已结束" : "进行中");
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
    private ChatMessage saveAiMessage(Long sessionId, Long userId, String content, String emotion) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole(MessageRoleEnum.ASSISTANT.getCode());
        msg.setContent(content);
        msg.setEmotion(emotion);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        log.debug("AI回复已保存: sessionId={}, msgId={}, emotion={}", sessionId, msg.getId(), emotion);
        return msg;
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
        if ("新的咨询".equals(session.getTitle()) || "新的心理咨询".equals(session.getTitle())) {
            String autoTitle = truncate(userContent.trim(), 20);
            session.setTitle(autoTitle);
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 构建会话上下文（最近 N 条消息作为对话历史，传给 AI）
     */
    private String buildContext(Long sessionId) {
        List<ChatMessage> recentMessages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .last("LIMIT 10")
        );

        if (recentMessages.isEmpty()) {
            return "";
        }

        // 倒序拼接为对话历史文本
        StringBuilder sb = new StringBuilder();
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ChatMessage m = recentMessages.get(i);
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

    /**
     * 情绪分析（关键词匹配）。
     * 后续可替换为情感分析模型或 AI 内置的情绪判断。
     */
    private String analyzeEmotion(String userContent) {
        String content = userContent.toLowerCase();
        if (content.contains("焦虑") || content.contains("紧张") || content.contains("担心")
                || content.contains("害怕") || content.contains("恐慌")) {
            return "焦虑";
        }
        if (content.contains("难过") || content.contains("抑郁") || content.contains("伤心")
                || content.contains("痛苦") || content.contains("绝望")) {
            return "低落";
        }
        if (content.contains("开心") || content.contains("高兴") || content.contains("快乐")
                || content.contains("愉快") || content.contains("兴奋")) {
            return "愉悦";
        }
        if (content.contains("愤怒") || content.contains("生气") || content.contains("烦躁")) {
            return "愤怒";
        }
        if (content.contains("失眠") || content.contains("睡不着") || content.contains("困倦")) {
            return "疲惫";
        }
        return "平静";
    }

    // ======================== VO 转换 ========================

    private ChatSessionVO toSessionVO(ChatSession session, String preview, Integer count) {
        Integer st = session.getStatus() == null ? 1 : session.getStatus();
        return ChatSessionVO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .status(st)
                .statusText(st == 2 ? "已结束" : "进行中")
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
                .build();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
