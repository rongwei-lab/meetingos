package com.meetingos.auth;

import com.meetingos.common.BusinessException;
import com.meetingos.security.AppUser;
import com.meetingos.security.AppUserRepository;
import com.meetingos.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final JwtService jwtService;
    private final LoginRateLimitService loginRateLimitService;

    public AuthService(AuthenticationManager authenticationManager,
                       AppUserRepository userRepository,
                       JwtService jwtService,
                       LoginRateLimitService loginRateLimitService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        loginRateLimitService.assertAllowed(request.username(), httpRequest);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (RuntimeException ex) {
            loginRateLimitService.recordFailure(request.username(), httpRequest, ex);
        }
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        loginRateLimitService.recordSuccess(request.username(), httpRequest);
        return new LoginResponse(
                jwtService.issueToken(user),
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoles()
        );
    }
}
