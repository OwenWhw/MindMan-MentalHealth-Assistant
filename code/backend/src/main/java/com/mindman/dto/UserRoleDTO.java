package com.mindman.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端修改用户角色请求
 */
@Data
public class UserRoleDTO {

    /** admin / user */
    @NotBlank(message = "角色不能为空")
    private String role;
}
