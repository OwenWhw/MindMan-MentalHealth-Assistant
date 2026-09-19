package com.mindman.ai;

/**
 * 一条对话消息（OpenAI 兼容格式）。
 *
 * @param role    角色：system / user / assistant
 * @param content 消息内容
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
