package com.mindman.service;

import com.mindman.common.page.PageVO;
import com.mindman.dto.ChangePasswordDTO;
import com.mindman.dto.LoginDTO;
import com.mindman.dto.LoginVO;
import com.mindman.dto.RegisterDTO;
import com.mindman.dto.UpdateProfileDTO;
import com.mindman.dto.UserAdminVO;
import com.mindman.entity.User;

/**
 * 用户服务
 */
public interface UserService {

    /** 登录，返回用户信息 + JWT Token */
    LoginVO login(LoginDTO dto);

    /** 注册新用户（注册成功即登录，返回 LoginVO） */
    LoginVO register(RegisterDTO dto);

    /** 获取当前登录用户信息 */
    User currentUser(Long userId);

    /** 退出登录 */
    void logout(Long userId);

    /** 编辑当前用户资料，返回更新后的用户信息 */
    User updateProfile(Long userId, UpdateProfileDTO dto);

    /** 修改当前用户密码 */
    void changePassword(Long userId, ChangePasswordDTO dto);

    // ======================== 管理端 ========================

    /**
     * 分页查询用户（管理端）
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param keyword  用户名/昵称模糊查询（空忽略）
     * @param role     角色筛选（空忽略）
     * @param status   状态筛选（null 忽略）
     * @return 分页视图
     */
    PageVO<UserAdminVO> adminPageUsers(int page, int pageSize, String keyword, String role, Integer status);

    /** 启用/禁用用户（管理端） */
    void adminSetStatus(Long id, Integer status);

    /** 修改用户角色（管理端） */
    void adminSetRole(Long id, String role);

    /** 删除用户（逻辑删除，管理端） */
    void adminDeleteUser(Long id);
}
