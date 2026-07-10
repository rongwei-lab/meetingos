package com.meetingos.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(SystemAdminAccessTest.TestConfig.class)
class SystemAdminAccessTest {

    @Autowired
    private ProtectedSystemService protectedSystemService;

    @Test
    @WithMockUser(username = "platform-root", roles = "SUPER_ADMIN")
    void customUsernameWithSuperAdminRoleCanAccessSystemOperation() {
        assertThat(protectedSystemService.readSystemSettings()).isEqualTo("ok");
    }

    @Test
    @WithMockUser(username = "meeting-operator", roles = "MEETING_ADMIN")
    void meetingAdminCannotAccessSystemOperation() {
        assertThatThrownBy(() -> protectedSystemService.readSystemSettings())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "MEETING_ADMIN")
    void legacyAdminUsernameWithoutSuperAdminRoleCannotAccessSystemOperation() {
        assertThatThrownBy(() -> protectedSystemService.readSystemSettings())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean("systemAdminAccess")
        SystemAdminAccess systemAdminAccess() {
            return new SystemAdminAccess();
        }

        @Bean
        ProtectedSystemService protectedSystemService() {
            return new ProtectedSystemService();
        }
    }

    static class ProtectedSystemService {

        @PreAuthorize("@systemAdminAccess.isAdminAccount()")
        public String readSystemSettings() {
            return "ok";
        }
    }
}
