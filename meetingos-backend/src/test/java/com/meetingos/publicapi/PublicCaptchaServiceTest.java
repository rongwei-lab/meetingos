package com.meetingos.publicapi;

import com.meetingos.common.BusinessException;
import com.meetingos.security.PublicQueryProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCaptchaServiceTest {

    @Test
    void capsChallengesAndReleasesExpiredEntries() {
        PublicCaptchaService service = new PublicCaptchaService(enabledProperties(), 2);

        service.create();
        service.create();
        assertThatThrownBy(service::create)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码服务繁忙");
        assertThat(service.challengeCount()).isEqualTo(2);

        service.cleanupExpiredEntries(Long.MAX_VALUE);
        assertThat(service.challengeCount()).isZero();

        service.create();
        assertThat(service.challengeCount()).isEqualTo(1);
    }

    @Test
    void unknownCaptchaIdReturnsBusinessErrorInsteadOfNullPointerException() {
        PublicCaptchaService service = new PublicCaptchaService(enabledProperties(), 10);

        assertThatThrownBy(() -> service.verify("missing-captcha", "4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码已过期");
    }

    @Test
    void allowsAtMostThreeConcurrentSuccessfulUses() throws Exception {
        PublicCaptchaService service = new PublicCaptchaService(enabledProperties(), 10);
        PublicCaptchaResponse captcha = service.create();
        String answer = answer(captcha.question());
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(ignored -> (Callable<Boolean>) () -> {
                        try {
                            service.verify(captcha.captchaId(), answer);
                            return true;
                        } catch (BusinessException ex) {
                            return false;
                        }
                    })
                    .toList();

            long successes = executor.invokeAll(attempts).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception ex) {
                            throw new AssertionError(ex);
                        }
                    })
                    .count();

            assertThat(successes).isEqualTo(3);
            assertThat(service.challengeCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private PublicQueryProperties enabledProperties() {
        PublicQueryProperties properties = new PublicQueryProperties();
        properties.setCaptchaEnabled(true);
        properties.setCaptchaTtlSeconds(300);
        return properties;
    }

    private String answer(String question) {
        String[] parts = question.replace("= ?", "").trim().split("\\s+\\+\\s+");
        return String.valueOf(Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]));
    }
}
