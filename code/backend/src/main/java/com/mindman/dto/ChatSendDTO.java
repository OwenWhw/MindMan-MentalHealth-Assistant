package com.mindman.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送聊天消息请求
 */
@Data
public class ChatSendDTO {

    /** 会话 ID */
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /** 消息内容 */
    @NotNull(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字")
    private String content;

    /** 模型名称（可选，默认使用配置的 model） */
    private String model;
}
