package com.mindman.dto;

import lombok.Data;

/**
 * 用户资料编辑（昵称 / 手机号 / 邮箱 / 头像）
 */
@Data
public class UpdateProfileDTO {
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
}
