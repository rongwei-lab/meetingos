package com.meetingos.publicapi;

import com.meetingos.common.BusinessException;
import com.meetingos.security.PublicQueryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicCaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CHALLENGES = 50_000;

    private final PublicQueryProperties properties;
    private final Map<String, CaptchaChallenge> challenges = new ConcurrentHashMap<>();
    private final Object capacityLock = new Object();
    private final int maxChallenges;

    @Autowired
    public PublicCaptchaService(PublicQueryProperties properties) {
        this(properties, MAX_CHALLENGES);
    }

    PublicCaptchaService(PublicQueryProperties properties, int maxChallenges) {
        this.properties = properties;
        this.maxChallenges = Math.max(1, maxChallenges);
    }

    public PublicCaptchaResponse create() {
        if (!properties.isCaptchaEnabled()) {
            return PublicCaptchaResponse.disabled();
        }
        cleanupExpired();
        int left = RANDOM.nextInt(8) + 2;
        int right = RANDOM.nextInt(8) + 2;
        String captchaId = UUID.randomUUID().toString();
        long expiresAt = Instant.now().plusSeconds(ttlSeconds()).toEpochMilli();
        synchronized (capacityLock) {
            if (challenges.size() >= maxChallenges) {
                cleanupExpired();
            }
            if (challenges.size() >= maxChallenges) {
                throw BusinessException.badRequest("验证码服务繁忙，请稍后再试");
            }
            challenges.put(captchaId, new CaptchaChallenge(String.valueOf(left + right), expiresAt));
        }
        return new PublicCaptchaResponse(true, captchaId, left + " + " + right + " = ?", ttlSeconds());
    }

    public boolean isEnabled() {
        return properties.isCaptchaEnabled();
    }

    public void verify(String captchaId, String answer) {
        verify(captchaId, answer, false);
    }

    /**
     * 综合查询只产生一次业务请求，验证成功后必须立即消费验证码，避免同一答案被重复查询利用。
     * 旧版座位、住宿、晚宴三个独立请求继续通过 {@link #verify(String, String)} 兼容最多三次成功。
     */
    public void verifyAndConsume(String captchaId, String answer) {
        verify(captchaId, answer, true);
    }

    private void verify(String captchaId, String answer, boolean consumeOnSuccess) {
        if (!properties.isCaptchaEnabled()) {
            return;
        }
        cleanupExpired();
        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            throw BusinessException.badRequest("请先完成验证码");
        }
        CaptchaChallenge challenge = challenges.get(captchaId);
        if (challenge == null) {
            throw BusinessException.badRequest("验证码已过期，请刷新后重试");
        }
        if (challenge.expired()) {
            challenges.remove(captchaId, challenge);
            throw BusinessException.badRequest("验证码已过期，请刷新后重试");
        }
        synchronized (challenge) {
            // 另一个并发请求可能已耗尽并删除当前验证码；不能让等待锁的请求继续复用旧对象。
            if (challenges.get(captchaId) != challenge || challenge.expired()) {
                challenges.remove(captchaId, challenge);
                throw BusinessException.badRequest("验证码已过期，请刷新后重试");
            }
            if (!challenge.answer.equals(answer.trim())) {
                challenge.failedAttempts++;
                if (challenge.failedAttempts >= 3) {
                    challenges.remove(captchaId, challenge);
                    throw BusinessException.badRequest("验证码错误次数过多，请刷新后重试");
                }
                throw BusinessException.badRequest("验证码不正确");
            }

            if (consumeOnSuccess) {
                challenges.remove(captchaId, challenge);
            } else {
                /*
                 * 兼容旧版页面同时发起“座位 + 住宿 + 晚宴”三个查询请求。原子递减保证
                 * 并发请求最多成功三次，避免多个线程读到同一个 remainingSuccessUses 后覆盖更新。
                 */
                challenge.remainingSuccessUses--;
                if (challenge.remainingSuccessUses <= 0) {
                    challenges.remove(captchaId, challenge);
                }
            }
        }
    }

    private void cleanupExpired() {
        cleanupExpiredEntries(Instant.now().toEpochMilli());
    }

    void cleanupExpiredEntries(long now) {
        Iterator<Map.Entry<String, CaptchaChallenge>> iterator = challenges.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CaptchaChallenge> entry = iterator.next();
            if (entry.getValue().expiresAt < now) {
                challenges.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private int ttlSeconds() {
        return Math.max(60, properties.getCaptchaTtlSeconds());
    }

    int challengeCount() {
        return challenges.size();
    }

    private static final class CaptchaChallenge {
        private final String answer;
        private final long expiresAt;
        private int remainingSuccessUses = 3;
        private int failedAttempts;

        private CaptchaChallenge(String answer, long expiresAt) {
            this.answer = answer;
            this.expiresAt = expiresAt;
        }

        boolean expired() {
            return expiresAt < Instant.now().toEpochMilli();
        }
    }
}
