package com.meetingos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final SecurityProperties properties;
    private final SecretKey signingKey;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(AppUser user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(Role::name).toList();
        return Jwts.builder()
                .issuer(properties.jwtIssuer())
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("displayName", user.getDisplayName())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public CurrentUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.jwtIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        @SuppressWarnings("unchecked")
        List<String> roleNames = claims.get("roles", List.class);
        Set<Role> roles = roleNames.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
        Long userId = ((Number) claims.get("uid")).longValue();
        return new CurrentUser(
                userId,
                claims.getSubject(),
                claims.get("displayName", String.class),
                roles
        );
    }
}
