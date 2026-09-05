package com.mindman.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO {

    private Long id;
    private Long sessionId;
    private String role;           // user / assistant
    private String content;
    private String emotion;        // AI 分析的情绪标签（仅 assistant 消息有值）
    private LocalDateTime createdAt;
}
