package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindman.common.enums.RoleEnum;
import com.mindman.common.enums.StatusEnum;
import com.mindman.common.exception.BadRequestException;
import com.mindman.common.exception.ForbiddenException;
import com.mindman.common.exception.NotFoundException;
import com.mindman.common.exception.UnauthorizedException;
import com.mindman.common.page.PageVO;
import com.mindman.dto.ChangePasswordDTO;
import com.mindman.dto.LoginDTO;
import com.mindman.dto.LoginVO;
import com.mindman.dto.RegisterDTO;
import com.mindman.dto.UpdateProfileDTO;
import com.mindman.dto.UserAdminVO;
import com.mindman.entity.User;
import com.mindman.mapper.UserMapper;
import com.mindman.service.UserService;
import com.mindman.util.JwtUtil;
import com.mindman.util.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * 用户登录服务实现
 *
 * <h3>业务讲解（登录流程）</h3>
 * <ol>
 *   <li>根据 username 查询用户（LambdaQueryWrapper）</li>
 *   <li>用户不存在 → 抛出 401</li>
 *   <li>用户被禁用 → 抛出 40301</li>
 *   <li>BCrypt 校验密码（自带盐值）</li>
 *   <li>签发 JWT，返回 LoginVO</li>
 * </ol>
 *
 * <h3>安全要点</h3>
 * <ul>
 *   <li>密码永不以明文存储或返回</li>
 *   <li>用户不存在 → "账号不存在"；密码错误 → "密码错误"（提升用户体验）</li>
 *   <li>JWT 默认 7 天过期，前端据 expiresAt 倒计时</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 查用户（支持用户名或手机号登录）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .and(w -> w.eq(User::getUsername, dto.getUsername())
                                .or().eq(User::getPhone, dto.getUsername()))
        );
        if (user == null) {
            // 用户不存在：明确告知，引导去注册
            throw new UnauthorizedException("账号不存在，请检查用户名/手机号或前往注册");
        }
        // 2. 状态检查
        if (user.getStatus() == StatusEnum.DISABLED.getCode()) {
            throw new ForbiddenException("账号已被禁用");
        }
        // 3. BCrypt 密码校验（自动比对盐值）
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            // 密码错误
            throw new UnauthorizedException("密码错误");
        }
        // 4. 签发 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("用户 {} 登录成功 id={}", user.getUsername(), user.getId());

        return buildLoginVO(user, token);
    }

    @Override
    @Transactional
    public LoginVO register(RegisterDTO dto) {
        // 1. 唯一性校验
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new BadRequestException("用户名已存在");
        }
        // 2. 构建实体（密码加密 + 默认角色/状态）
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail().trim());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone().trim());
        }
        user.setRole(RoleEnum.USER.getCode());
        user.setStatus(StatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        // 3. 注册即登录
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("新用户 {} 注册成功 id={}", user.getUsername(), user.getId());
        return buildLoginVO(user, token);
    }

    @Override
    public User currentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    @Override
    public void logout(Long userId) {
        // JWT 无状态：客户端清除 token，服务端仅记日志
        // 如需服务端主动失效：将 token 加入 Redis 黑名单（SETEX key ttl value）
        log.info("用户 {} 退出登录", userId);
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = requireUser(userId);
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            user.setNickname(dto.getNickname().trim());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone().trim());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail().trim());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar().trim());
        }
        userMapper.updateById(user);
        log.info("用户 {} 更新资料", userId);
        // 返回前清空密码，避免泄露
        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("原密码不正确");
        }
        if (dto.getNewPassword().equals(dto.getOldPassword())) {
            throw new BadRequestException("新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户 {} 修改密码", userId);
    }

    // ======================== 管理端 ========================

    @Override
    public PageVO<UserAdminVO> adminPageUsers(int page, int pageSize, String keyword, String role, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, k).or().like(User::getNickname, k));
        }
        if (role != null && !role.isBlank()) {
            wrapper.eq(User::getRole, role.trim());
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreatedAt);

        IPage<User> paged = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        java.util.List<UserAdminVO> list = paged.getRecords().stream()
                .map(UserAdminVO::from)
                .collect(Collectors.toList());
        return PageVO.of(paged.getTotal(), page, pageSize, list);
    }

    @Override
    @Transactional
    public void adminSetStatus(Long id, Integer status) {
        User u = requireUser(id);
        if (u.getId().equals(LoginUser.get())) {
            throw new BadRequestException("不能操作当前登录账号的状态");
        }
        u.setStatus(status);
        userMapper.updateById(u);
        log.info("管理端将用户 {} 状态置为 {}", id, status);
    }

    @Override
    @Transactional
    public void adminSetRole(Long id, String role) {
        User u = requireUser(id);
        if (!RoleEnum.ADMIN.getCode().equals(role) && !RoleEnum.USER.getCode().equals(role)) {
            throw new BadRequestException("非法的角色值");
        }
        if (u.getId().equals(LoginUser.get())) {
            throw new BadRequestException("不能修改当前登录账号的角色");
        }
        // 保护：不允许把最后一个启用状态的管理员降级为普通用户
        if (RoleEnum.ADMIN.getCode().equals(u.getRole()) && RoleEnum.USER.getCode().equals(role)) {
            long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, RoleEnum.ADMIN.getCode())
                    .eq(User::getStatus, StatusEnum.ENABLED.getCode()));
            if (adminCount <= 1) {
                throw new BadRequestException("至少保留一个启用状态的管理员");
            }
        }
        u.setRole(role);
        userMapper.updateById(u);
        log.info("管理端将用户 {} 角色置为 {}", id, role);
    }

    @Override
    @Transactional
    public void adminDeleteUser(Long id) {
        User u = requireUser(id);
        if (u.getId().equals(LoginUser.get())) {
            throw new BadRequestException("不能删除当前登录账号");
        }
        userMapper.deleteById(id);
        log.info("管理端删除用户 id={}", id);
    }

    private User requireUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new NotFoundException("用户不存在");
        }
        return u;
    }

    // ── 私有：构建登录响应 ──

    private LoginVO buildLoginVO(User user, String token) {
        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .token(token)
                .expiresAt(jwtUtil.getExpiration(token))
                .build();
    }
}
