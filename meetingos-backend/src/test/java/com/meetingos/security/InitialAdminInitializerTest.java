package com.meetingos.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialAdminInitializerTest {

    @Test
    void createsConfiguredUsernameWithSuperAdminAuthority() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        InitialAdminProperties properties = new InitialAdminProperties();
        properties.setUsername(" Platform-Root ");
        properties.setPassword("Strong-Initial-Password-2026");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.findByUsername("platform-root")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Strong-Initial-Password-2026")).thenReturn("encoded-password");

        new InitialAdminInitializer(userRepository, passwordEncoder, properties).run(mock(ApplicationArguments.class));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        AppUser created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("platform-root");
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getRoles()).contains(Role.SUPER_ADMIN, Role.MEETING_ADMIN);
    }
}
