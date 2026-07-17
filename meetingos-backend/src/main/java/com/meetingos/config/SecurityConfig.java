package com.meetingos.config;

import com.meetingos.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/api/auth/login", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/images/**").permitAll()
                        // 公开入口常被监控、curl -I 或链接预览用 HEAD 探测；权限应与 GET 保持一致。
                        .requestMatchers(HttpMethod.HEAD, "/uploads/images/**", "/r/**", "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/tduck/webhook", "/api/public/tduck/webhook", "/api/integrations/tduck/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/invitations/**").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/public/seat-query",
                                "/api/public/room-query",
                                "/api/public/dinner-query",
                                "/api/public/combined-query",
                                "/api/public/checkin",
                                "/api/public/checkin-preview"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/r/**", "/api/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
