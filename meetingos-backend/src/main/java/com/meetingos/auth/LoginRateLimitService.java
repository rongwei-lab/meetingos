package com.meetingos.auth;

import com.meetingos.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginRateLimitService {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 15 * 60_000L;
    private static final long LOCK_MILLIS = 15 * 60_000L;

    private final Map<String, LoginFailures> failuresByKey = new ConcurrentHashMap<>();

    public void assertAllowed(String username, HttpServletRequest request) {
        LoginFailures failures = failuresByKey.get(key(username, request));
        if (failures == null) {
            return;
        }
        synchronized (failures) {
            if (failures.lockedUntil > Instant.now().toEpochMilli()) {
                throw BusinessException.badRequest("登录失败次数过多，请稍后再试");
            }
        }
    }

    public void recordSuccess(String username, HttpServletRequest request) {
        failuresByKey.remove(key(username, request));
    }

    public void recordFailure(String username, HttpServletRequest request, RuntimeException cause) {
        String key = key(username, request);
        LoginFailures failures = failuresByKey.computeIfAbsent(key, ignored -> new LoginFailures());
        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW_MILLIS;
        synchronized (failures) {
            while (!failures.failureTimes.isEmpty() && failures.failureTimes.peekFirst() < windowStart) {
                failures.failureTimes.removeFirst();
            }
            failures.failureTimes.addLast(now);
            if (failures.failureTimes.size() >= MAX_FAILURES) {
                failures.lockedUntil = now + LOCK_MILLIS;
            }
        }
        /*
         * 对外仍返回统一的用户名/密码错误，避免登录接口变成账号枚举通道。
         * 真正触发锁定的请求会在下一次 assertAllowed 中收到“稍后再试”。
         */
        throw new BadCredentialsException("用户名或密码错误", cause);
    }

    private String key(String username, HttpServletRequest request) {
        return normalizeUsername(username) + "|" + clientIp(request);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "unknown";
        }
        return request.getRemoteAddr();
    }

    private static final class LoginFailures {
        private final Deque<Long> failureTimes = new ArrayDeque<>();
        private long lockedUntil;
    }
}
