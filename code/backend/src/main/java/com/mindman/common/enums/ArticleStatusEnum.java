package com.mindman.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文章状态
 */
@Getter
@AllArgsConstructor
public enum ArticleStatusEnum {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;
}