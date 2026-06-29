package com.meetingos.user;

import com.meetingos.common.BusinessException;
import com.meetingos.security.AppUser;
import com.meetingos.security.AppUserRepository;
import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private AppUserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void updateChangesNormalUserProfileStatusAndRoles() {
        AppUser user = user("operator", "操作员", UserStatus.ACTIVE, Set.of(Role.VIEWER));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        UserResponse response = userService.update(10L, new UpdateUserRequest(
                "签到负责人",
                UserStatus.DISABLED,
                Set.of(Role.CHECKIN_STAFF)
        ));

        assertThat(response.displayName()).isEqualTo("签到负责人");
        assertThat(response.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(response.roles()).containsExactly(Role.CHECKIN_STAFF);
        assertThat(user.getDisplayName()).isEqualTo("签到负责人");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.getRoles()).containsExactly(Role.CHECKIN_STAFF);
        verify(userRepository).findById(10L);
    }

    @Test
    void updateRejectsDisablingBuiltInAdmin() {
        AppUser admin = user("admin", "超级管理员", UserStatus.ACTIVE, Set.of(Role.SUPER_ADMIN, Role.MEETING_ADMIN));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.update(1L, new UpdateUserRequest(
                "超级管理员",
                UserStatus.DISABLED,
                Set.of(Role.SUPER_ADMIN)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能停用");
    }

    @Test
    void updateRejectsRemovingSuperAdminFromBuiltInAdmin() {
        AppUser admin = user("admin", "超级管理员", UserStatus.ACTIVE, Set.of(Role.SUPER_ADMIN, Role.MEETING_ADMIN));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.update(1L, new UpdateUserRequest(
                "超级管理员",
                UserStatus.ACTIVE,
                Set.of(Role.MEETING_ADMIN)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须保留超级管理员");
    }

    @Test
    void listStillReturnsUsersAfterAddingUpdateGuards() {
        AppUser user = user("operator", "操作员", UserStatus.ACTIVE, Set.of(Role.VIEWER));
        when(userRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of(user)));

        var response = userService.list(null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).username()).isEqualTo("operator");
    }

    private AppUser user(String username, String displayName, UserStatus status, Set<Role> roles) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setPasswordHash("encoded-password");
        user.setRoles(new LinkedHashSet<>(roles));
        return user;
    }
}
