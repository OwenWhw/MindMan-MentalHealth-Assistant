package com.mindman.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话详情 VO（含最近消息预览）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionVO {

    private Long id;
    private String title;
    private Integer status;        // 1进行中 2已结束
    private String statusText;     // 进行中 / 已结束
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 最近一条消息预览（用于列表展示） */
    private String lastMessagePreview;

    /** 消息数量 */
    private Integer messageCount;

    /** 未读消息数（暂定0，后续接入推送时计算） */
    private Integer unreadCount;
}
