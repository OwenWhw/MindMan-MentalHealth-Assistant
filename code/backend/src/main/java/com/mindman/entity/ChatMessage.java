package com.mindman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天消息
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    private String role;           // user / assistant
    private String content;
    private String emotion;        // AI 分析的情绪维度
    @TableField("delivery_status")
    private String deliveryStatus; // streaming / complete / interrupted / failed

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
