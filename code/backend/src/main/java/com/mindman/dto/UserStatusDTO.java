package com.mindman.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端修改用户状态（启用/禁用）请求
 */
@Data
public class UserStatusDTO {

    /** 1=启用，0=禁用 */
    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;
}
