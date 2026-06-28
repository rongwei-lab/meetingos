package com.meetingos.publicapi;

import com.meetingos.common.BusinessException;
import com.meetingos.security.PublicQueryProperties;
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

    private final PublicQueryProperties properties;
    private final Map<String, CaptchaChallenge> challenges = new ConcurrentHashMap<>();

    public PublicCaptchaService(PublicQueryProperties properties) {
        this.properties = properties;
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
        challenges.put(captchaId, new CaptchaChallenge(String.valueOf(left + right), expiresAt, 2, 0));
        return new PublicCaptchaResponse(true, captchaId, left + " + " + right + " = ?", ttlSeconds());
    }

    public boolean isEnabled() {
        return properties.isCaptchaEnabled();
    }

    public void verify(String captchaId, String answer) {
        if (!properties.isCaptchaEnabled()) {
            return;
        }
        cleanupExpired();
        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            throw BusinessException.badRequest("请先完成验证码");
        }
        CaptchaChallenge challenge = challenges.get(captchaId);
        if (challenge == null || challenge.expired()) {
            challenges.remove(captchaId);
            throw BusinessException.badRequest("验证码已过期，请刷新后重试");
        }
        if (!challenge.answer().equals(answer.trim())) {
            CaptchaChallenge failed = challenge.withFailedAttempts(challenge.failedAttempts() + 1);
            if (failed.failedAttempts() >= 3) {
                challenges.remove(captchaId);
                throw BusinessException.badRequest("验证码错误次数过多，请刷新后重试");
            }
            challenges.put(captchaId, failed);
            throw BusinessException.badRequest("验证码不正确");
        }
        /*
         * 综合查询页会同时发起“座位 + 住宿”两个请求。短时间内允许同一个验证码再通过一次，
         * 避免用户明明答对却因为并发第二个请求失败；超过两次后删除，防止验证码被长期复用。
         */
        if (challenge.remainingSuccessUses() <= 1) {
            challenges.remove(captchaId);
        } else {
            challenges.put(captchaId, challenge.withRemainingSuccessUses(challenge.remainingSuccessUses() - 1));
        }
    }

    private void cleanupExpired() {
        long now = Instant.now().toEpochMilli();
        Iterator<Map.Entry<String, CaptchaChallenge>> iterator = challenges.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt() < now) {
                iterator.remove();
            }
        }
    }

    private int ttlSeconds() {
        return Math.max(60, properties.getCaptchaTtlSeconds());
    }

    private record CaptchaChallenge(String answer, long expiresAt, int remainingSuccessUses, int failedAttempts) {

        boolean expired() {
            return expiresAt < Instant.now().toEpochMilli();
        }

        CaptchaChallenge withRemainingSuccessUses(int nextRemainingSuccessUses) {
            return new CaptchaChallenge(answer, expiresAt, nextRemainingSuccessUses, failedAttempts);
        }

        CaptchaChallenge withFailedAttempts(int nextFailedAttempts) {
            return new CaptchaChallenge(answer, expiresAt, remainingSuccessUses, nextFailedAttempts);
        }
    }
}
