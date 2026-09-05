package com.mindman.dto;

import com.mindman.entity.User;

import java.time.format.DateTimeFormatter;

/**
 * 管理端用户视图对象。
 *
 * <p>与 {@link User} 实体对齐，但剔除 {@code password} 等敏感字段，
 * 供管理后台用户列表/详情展示使用。</p>
 */
public class UserAdminVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private String role;
    private Integer status;
    private String createdAt;
    private String updatedAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static UserAdminVO from(User u) {
        if (u == null) return null;
        UserAdminVO vo = new UserAdminVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setEmail(u.getEmail());
        vo.setPhone(u.getPhone());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        if (u.getCreatedAt() != null) vo.setCreatedAt(u.getCreatedAt().format(FMT));
        if (u.getUpdatedAt() != null) vo.setUpdatedAt(u.getUpdatedAt().format(FMT));
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
