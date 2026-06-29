package com.meetingos.user;

import com.meetingos.common.BusinessException;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.security.AppUser;
import com.meetingos.security.AppUserRepository;
import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private static final String BUILT_IN_ADMIN_USERNAME = "admin";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String keyword, UserStatus status, int page, int size) {
        Page<AppUser> users = userRepository.search(blankToNull(keyword), status, PageRequests.of(page, size));
        return PageResponse.from(users.map(this::toResponse));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        validatePassword(request.password());
        userRepository.findByUsername(username).ifPresent(existing -> {
            throw BusinessException.conflict("用户名已存在");
        });

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(request.status() == null ? UserStatus.ACTIVE : request.status());
        user.setRoles(normalizeRoles(request.roles()));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        ensureBuiltInAdminKeepsSystemAccess(user, request);
        user.setDisplayName(request.displayName().trim());
        user.setStatus(request.status());
        user.setRoles(normalizeRoles(request.roles()));
        return toResponse(user);
    }

    @Transactional
    public UserResponse resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        validatePassword(request.password());
        // 只保存编码后的密码。响应仍使用 UserResponse，避免把 passwordHash 带回前端或日志链路。
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return toResponse(user);
    }

    public UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                new LinkedHashSet<>(user.getRoles()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private Set<Role> normalizeRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw BusinessException.badRequest("用户至少需要一个角色");
        }
        return new LinkedHashSet<>(roles);
    }

    private void ensureBuiltInAdminKeepsSystemAccess(AppUser user, UpdateUserRequest request) {
        if (!BUILT_IN_ADMIN_USERNAME.equals(user.getUsername())) {
            return;
        }
        /*
         * 系统设置入口由 SystemAdminAccess 固定识别内置 admin 账号。
         * 因此 admin 不能被停用，也不能移除 SUPER_ADMIN，否则会把用户管理、
         * 品牌配置、TDuck 接入等全局设置全部锁死，只能手工改库恢复。
         */
        if (request.status() != UserStatus.ACTIVE) {
            throw BusinessException.badRequest("内置 admin 账号不能停用");
        }
        if (request.roles() == null || !request.roles().contains(Role.SUPER_ADMIN)) {
            throw BusinessException.badRequest("内置 admin 账号必须保留超级管理员角色");
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw BusinessException.badRequest("用户名不能为空");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        String value = password == null ? "" : password.trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        if (value.length() < 12
                || normalized.equals("123456")
                || normalized.equals("admin123")
                || normalized.startsWith("change_me")
                || normalized.equals("password")
                || normalized.equals("password123")) {
            throw BusinessException.badRequest("密码必须至少 12 位，且不能使用默认或常见弱口令");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
