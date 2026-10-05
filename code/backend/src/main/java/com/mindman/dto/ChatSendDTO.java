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

    /** 可选的对话展示文本；用于让内置快捷操作显示简短名称，而不暴露内部指令 */
    @Size(max = 2000, message = "展示内容不能超过2000字")
    private String displayContent;

    /** 模型名称（可选，默认使用配置的 model） */
    private String model;

    /** 用户主动开启时，将本人近30天的情绪花园记录加入本轮上下文 */
    private boolean includeGardenContext;

    /** 用户从知识文章页带入的文章 ID；服务端只接受已发布文章 */
    private Long referenceArticleId;

    /** 本轮是否为围绕所选文章的翻译任务；用于约束检索和回复格式 */
    private boolean articleTranslationMode;

    public String getDisplayContentOrContent() {
        return displayContent == null || displayContent.isBlank() ? content : displayContent;
    }
}
