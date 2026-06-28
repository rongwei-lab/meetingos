package com.meetingos.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingos.init-admin")
public class InitialAdminProperties {

    /**
     * 首次启动且系统没有任何用户时创建的超级管理员用户名。
     */
    private String username = "admin";

    /**
     * 首次初始化密码。生产必须通过 MEETINGOS_INIT_ADMIN_PASSWORD 设置强密码。
     */
    private String password = "";

    /**
     * 当前用户表没有 email 字段，先保留配置绑定，避免 .env 声明与后端配置脱节。
     */
    private String email = "";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
