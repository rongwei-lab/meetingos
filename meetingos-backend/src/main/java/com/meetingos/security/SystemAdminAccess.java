package com.meetingos.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("systemAdminAccess")
public class SystemAdminAccess {

    private static final String SUPER_ADMIN_AUTHORITY = "ROLE_" + Role.SUPER_ADMIN.name();

    public boolean isAdminAccount() {
        /*
         * 初始超级管理员用户名允许通过环境变量自定义，因此用户名不能作为系统权限边界。
         * JWT 过滤器和 UserDetailsService 都只从数据库角色生成 GrantedAuthority；这里读取当前
         * Authentication 的不可变 SUPER_ADMIN 权限，既支持自定义用户名，也不会放行 MEETING_ADMIN。
         */
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> SUPER_ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}
