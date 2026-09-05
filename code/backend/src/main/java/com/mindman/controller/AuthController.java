package com.mindman.controller;

import com.mindman.common.R;
import com.mindman.dto.ChangePasswordDTO;
import com.mindman.dto.LoginDTO;
import com.mindman.dto.LoginVO;
import com.mindman.dto.RegisterDTO;
import com.mindman.dto.UpdateProfileDTO;
import com.mindman.entity.User;
import com.mindman.service.UserService;
import com.mindman.util.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证模块 - 登录 / 注册 / 当前用户 / 登出
 */
@Slf4j
@Tag(name = "认证模块", description = "登录 / 注册 / 当前用户 / 登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        log.info("用户 {} 尝试登录", dto.getUsername());
        return R.ok(userService.login(dto));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        log.info("新用户 {} 注册", dto.getUsername());
        // 注册成功即返回 LoginVO，前端可直接进入登录态
        return R.created(userService.register(dto));
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public R<User> me() {
        Long uid = LoginUser.get();
        return R.ok(userService.currentUser(uid));
    }

    @Operation(summary = "编辑当前用户资料")
    @PutMapping("/profile")
    public R<User> updateProfile(@RequestBody UpdateProfileDTO dto) {
        return R.ok(userService.updateProfile(LoginUser.get(), dto));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public R<?> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(LoginUser.get(), dto);
        return R.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<?> logout() {
        userService.logout(LoginUser.get());
        return R.ok();
    }
}
