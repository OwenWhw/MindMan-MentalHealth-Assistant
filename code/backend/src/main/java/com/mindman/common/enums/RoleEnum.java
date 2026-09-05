package com.mindman.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {

    USER("user", "普通用户"),
    ADMIN("admin", "管理员");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;
}