package com.mindman.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聊天消息角色
 */
@Getter
@AllArgsConstructor
public enum MessageRoleEnum {

    USER("user", "用户"),
    ASSISTANT("assistant", "AI助手");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;
}