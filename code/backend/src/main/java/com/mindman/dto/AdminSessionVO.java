package com.mindman.dto;

import java.time.format.DateTimeFormatter;

/**
 * 管理端咨询会话视图对象。
 *
 * <p>字段对齐前端 {@code records.vue}：sessionId / userId / userName / avatar / emotion /
 * lastSender / lastTime / lastMessage / messageCount（另含 status/statusText/startedAt/endedAt）。</p>
 */
public class AdminSessionVO {

    private Long sessionId;
    private Long userId;
    private String userName;
    private String avatar;
    private String emotion;
    private String lastSender;   // 用户 / AI
    private String lastTime;     // yyyy-MM-dd HH:mm:ss
    private String lastMessage;
    private Integer messageCount;
    private Integer status;
    private String statusText;
    private String startedAt;    // yyyy-MM-dd HH:mm:ss
    private String endedAt;      // yyyy-MM-dd HH:mm:ss

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static AdminSessionVO empty() {
        return new AdminSessionVO();
    }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public String getLastSender() { return lastSender; }
    public void setLastSender(String lastSender) { this.lastSender = lastSender; }
    public String getLastTime() { return lastTime; }
    public void setLastTime(String lastTime) { this.lastTime = lastTime; }
    public void setLastTime(java.time.LocalDateTime t) { this.lastTime = t == null ? null : t.format(FMT); }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public void setStartedAt(java.time.LocalDateTime t) { this.startedAt = t == null ? null : t.format(FMT); }
    public String getEndedAt() { return endedAt; }
    public void setEndedAt(String endedAt) { this.endedAt = endedAt; }
    public void setEndedAt(java.time.LocalDateTime t) { this.endedAt = t == null ? null : t.format(FMT); }
}
