package com.meetingos.user;

import com.meetingos.common.BusinessException;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.security.AppUser;
import com.meetingos.security.AppUserRepository;
import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String keyword, UserStatus status, int page, int size) {
        // 用户管理会连续读取全部分页；固定顺序可避免并发新增账号时跨页重复或遗漏。
        Page<AppUser> users = userRepository.search(
                blankToNull(keyword),
                status,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
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
        ensureActiveSuperAdminRemains(user, request);
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

    private void ensureActiveSuperAdminRemains(AppUser user, UpdateUserRequest request) {
        boolean currentlyProvidesSystemAccess = user.getStatus() == UserStatus.ACTIVE
                && user.getRoles().contains(Role.SUPER_ADMIN);
        boolean keepsSystemAccess = request.status() == UserStatus.ACTIVE
                && request.roles() != null
                && request.roles().contains(Role.SUPER_ADMIN);
        if (!currentlyProvidesSystemAccess || keepsSystemAccess) {
            return;
        }

        /*
         * 系统管理权限只由活跃账号的 SUPER_ADMIN 角色决定。允许在存在其他活跃超级管理员时
         * 调整当前账号，但禁止停用或降级最后一个超级管理员，避免只能通过手工改库恢复。
         */
        boolean hasAnotherActiveSuperAdmin = userRepository.findAll().stream()
                .filter(candidate -> !sameUser(candidate, user))
                .anyMatch(candidate -> candidate.getStatus() == UserStatus.ACTIVE
                        && candidate.getRoles().contains(Role.SUPER_ADMIN));
        if (hasAnotherActiveSuperAdmin) {
            return;
        }
        if (request.status() != UserStatus.ACTIVE) {
            throw BusinessException.badRequest("最后一个启用的超级管理员账号不能停用");
        }
        if (request.roles() == null || !request.roles().contains(Role.SUPER_ADMIN)) {
            throw BusinessException.badRequest("最后一个启用的超级管理员账号必须保留超级管理员角色");
        }
    }

    private boolean sameUser(AppUser first, AppUser second) {
        if (first == second) {
            return true;
        }
        return first.getId() != null && first.getId().equals(second.getId());
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
        if (value.length() < 6
                || normalized.equals("123456")
                || normalized.equals("admin123")
                || normalized.startsWith("change_me")
                || normalized.equals("password")
                || normalized.equals("password123")) {
            throw BusinessException.badRequest("密码必须至少 6 位，且不能使用默认或常见弱口令");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
