package com.meetingos.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class JwtCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "meetingos_access_token";

    private final SecurityProperties properties;

    public JwtCookieService(SecurityProperties properties) {
        this.properties = properties;
    }

    public void attachLoginCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(properties.accessTokenMinutes()))
                .build()
                .toString());
    }

    public void clearLoginCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }
}
