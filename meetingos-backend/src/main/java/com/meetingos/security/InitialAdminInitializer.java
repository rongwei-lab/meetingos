package com.meetingos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
@Profile("!test")
public class InitialAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InitialAdminProperties properties;

    public InitialAdminInitializer(AppUserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   InitialAdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        disableLegacyWeakAdminIfPresent();
        if (hasActiveSuperAdmin()) {
            return;
        }
        String username = normalizeUsername(properties.getUsername());
        String password = properties.getPassword() == null ? "" : properties.getPassword().trim();
        validateInitialPassword(password);

        AppUser admin = userRepository.findByUsername(username).orElseGet(AppUser::new);
        admin.setUsername(username);
        admin.setDisplayName("超级管理员");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setRoles(new LinkedHashSet<>(List.of(Role.SUPER_ADMIN, Role.MEETING_ADMIN)));
        userRepository.save(admin);
        log.info("Created initial MeetingOS admin user '{}'.", username);
    }

    private boolean hasActiveSuperAdmin() {
        return userRepository.findAll().stream()
                .anyMatch(user -> user.getStatus() == UserStatus.ACTIVE && user.getRoles().contains(Role.SUPER_ADMIN));
    }

    private void disableLegacyWeakAdminIfPresent() {
        userRepository.findByUsername("admin")
                .filter(user -> "{noop}admin123".equals(user.getPasswordHash()))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    user.setStatus(UserStatus.DISABLED);
                    log.warn("Disabled legacy weak default admin account. Configure MEETINGOS_INIT_ADMIN_* or reset the password before using this account.");
                });
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("初始管理员用户名不能为空");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private void validateInitialPassword(String password) {
        if (password.isBlank()
                || password.length() < 12
                || password.equalsIgnoreCase("admin123")
                || password.equals("123456")
                || password.toUpperCase(Locale.ROOT).startsWith("CHANGE_ME")) {
            throw new IllegalStateException("MEETINGOS_INIT_ADMIN_PASSWORD 必须设置为至少 12 位的非默认强密码");
        }
    }
}
