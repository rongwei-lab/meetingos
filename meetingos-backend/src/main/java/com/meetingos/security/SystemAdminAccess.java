package com.meetingos.security;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component("systemAdminAccess")
public class SystemAdminAccess {

    private static final String SYSTEM_ADMIN_USERNAME = "admin";

    public boolean isAdminAccount() {
        // 系统设置涉及全局品牌、接入配置、后台用户和会议授权，业务规则要求只认内置 admin 账号。
        // 即使其他账号拥有 SUPER_ADMIN 角色，也不能进入系统设置页或调用对应接口。
        return SecurityUtils.currentUser()
                .map(CurrentUser::username)
                .map(this::normalizeUsername)
                .map(SYSTEM_ADMIN_USERNAME::equals)
                .orElse(false);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
