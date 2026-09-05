package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.common.page.PageVO;
import com.mindman.dto.UserAdminVO;
import com.mindman.dto.UserRoleDTO;
import com.mindman.dto.UserStatusDTO;
import com.mindman.service.UserService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端用户管理控制器。
 *
 * <h3>接口列表</h3>
 * <pre>
 * GET    /api/admin/users                分页查询用户
 * PUT    /api/admin/users/{id}/status   启用/禁用用户
 * PUT    /api/admin/users/{id}/role     修改角色
 * DELETE /api/admin/users/{id}          删除用户
 * </pre>
 *
 * <p>所有接口均需管理员权限（{@link LoginUser#requireAdmin()}）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "用户管理（管理端）", description = "用户列表 / 启用禁用 / 角色调整 / 删除")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "分页查询用户（管理端）")
    public R<PageVO<UserAdminVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        LoginUser.requireAdmin();
        return R.page(userService.adminPageUsers(page, pageSize, keyword, role, parseStatus(status)));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用用户（管理端）")
    public R<Void> setStatus(@PathVariable Long id, @Valid @RequestBody UserStatusDTO dto) {
        LoginUser.requireAdmin();
        userService.adminSetStatus(id, dto.getStatus());
        return R.ok();
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "修改用户角色（管理端）")
    public R<Void> setRole(@PathVariable Long id, @Valid @RequestBody UserRoleDTO dto) {
        LoginUser.requireAdmin();
        userService.adminSetRole(id, dto.getRole());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户（管理端）")
    public R<Void> delete(@PathVariable Long id) {
        LoginUser.requireAdmin();
        userService.adminDeleteUser(id);
        return R.ok();
    }

    private Integer parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Integer.parseInt(status.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
