package com.mindman.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class ChatSessionCreateDTO {

    /** 会话标题（可选，不传则由系统根据首条消息自动生成） */
    @Size(max = 50, message = "标题不能超过50字")
    private String title;
}
