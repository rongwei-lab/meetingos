package com.meetingos.auth;

import com.meetingos.common.BusinessException;
import com.meetingos.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LoginRateLimitService {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 15 * 60_000L;
    private static final long LOCK_MILLIS = 15 * 60_000L;
    private static final long ENTRY_TTL_MILLIS = WINDOW_MILLIS + LOCK_MILLIS;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final int MAX_USERNAME_LENGTH = 64;

    private final Map<String, LoginFailures> failuresByKey = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupAt = new AtomicLong();
    private final Object capacityLock = new Object();
    private final ClientIpResolver clientIpResolver;
    private final int maxTrackedKeys;

    @Autowired
    public LoginRateLimitService(ClientIpResolver clientIpResolver) {
        this(clientIpResolver, MAX_TRACKED_KEYS);
    }

    LoginRateLimitService(ClientIpResolver clientIpResolver, int maxTrackedKeys) {
        this.clientIpResolver = clientIpResolver;
        this.maxTrackedKeys = Math.max(1, maxTrackedKeys);
    }

    public void assertAllowed(String username, HttpServletRequest request) {
        long now = Instant.now().toEpochMilli();
        cleanupIfNeeded(now);
        String key = key(username, request);
        LoginFailures failures = failuresByKey.get(key);
        if (failures == null) {
            return;
        }
        synchronized (failures) {
            if (failures.expired(now)) {
                failuresByKey.remove(key, failures);
                return;
            }
            failures.lastTouchedAt = now;
            if (failures.lockedUntil > now) {
                throw BusinessException.badRequest("登录失败次数过多，请稍后再试");
            }
        }
    }

    public void recordSuccess(String username, HttpServletRequest request) {
        failuresByKey.remove(key(username, request));
    }

    public void recordFailure(String username, HttpServletRequest request, RuntimeException cause) {
        String key = key(username, request);
        long now = Instant.now().toEpochMilli();
        cleanupIfNeeded(now);
        LoginFailures failures = getOrCreate(key, now);
        if (failures == null) {
            // 缓存满载时仍返回统一认证失败，但不再接受新的随机 key，保证内存上界。
            throw new BadCredentialsException("用户名或密码错误", cause);
        }
        long windowStart = now - WINDOW_MILLIS;
        synchronized (failures) {
            while (!failures.failureTimes.isEmpty() && failures.failureTimes.peekFirst() < windowStart) {
                failures.failureTimes.removeFirst();
            }
            failures.failureTimes.addLast(now);
            if (failures.failureTimes.size() >= MAX_FAILURES) {
                failures.lockedUntil = now + LOCK_MILLIS;
            }
            failures.lastTouchedAt = now;
        }
        /*
         * 对外仍返回统一的用户名/密码错误，避免登录接口变成账号枚举通道。
         * 真正触发锁定的请求会在下一次 assertAllowed 中收到“稍后再试”。
         */
        throw new BadCredentialsException("用户名或密码错误", cause);
    }

    private String key(String username, HttpServletRequest request) {
        return normalizeUsername(username) + "|" + clientIpResolver.resolve(request);
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        // 控制器校验负责拒绝超长用户名；这里再截断一次，保护未来内部调用不制造超大限流 key。
        return normalized.length() <= MAX_USERNAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_USERNAME_LENGTH);
    }

    private LoginFailures getOrCreate(String key, long now) {
        LoginFailures existing = failuresByKey.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (capacityLock) {
            existing = failuresByKey.get(key);
            if (existing != null) {
                return existing;
            }
            if (failuresByKey.size() >= maxTrackedKeys) {
                cleanupExpiredEntries(now);
            }
            if (failuresByKey.size() >= maxTrackedKeys) {
                return null;
            }
            LoginFailures created = new LoginFailures(now);
            failuresByKey.put(key, created);
            return created;
        }
    }

    private void cleanupIfNeeded(long now) {
        long lastCleanup = lastCleanupAt.get();
        if (now - lastCleanup < CLEANUP_INTERVAL_MILLIS || !lastCleanupAt.compareAndSet(lastCleanup, now)) {
            return;
        }
        cleanupExpiredEntries(now);
    }

    void cleanupExpiredEntries(long now) {
        failuresByKey.forEach((key, failures) -> {
            synchronized (failures) {
                if (failures.expired(now)) {
                    failuresByKey.remove(key, failures);
                }
            }
        });
    }

    int trackedKeyCount() {
        return failuresByKey.size();
    }

    private static final class LoginFailures {
        private final Deque<Long> failureTimes = new ArrayDeque<>();
        private long lockedUntil;
        private long lastTouchedAt;

        private LoginFailures(long now) {
            this.lastTouchedAt = now;
        }

        private boolean expired(long now) {
            return lockedUntil <= now && now - lastTouchedAt >= ENTRY_TTL_MILLIS;
        }
    }
}
