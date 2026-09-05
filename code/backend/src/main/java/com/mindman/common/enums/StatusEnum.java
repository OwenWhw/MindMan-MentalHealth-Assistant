package com.mindman.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启用/禁用状态枚举
 */
@Getter
@AllArgsConstructor
public enum StatusEnum {

    ENABLED(1, "启用"),
    DISABLED(0, "禁用");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;

    public static StatusEnum of(Integer code) {
        if (code == null) return null;
        for (StatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }
}