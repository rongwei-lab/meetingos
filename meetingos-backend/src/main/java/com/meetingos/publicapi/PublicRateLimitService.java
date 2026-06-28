package com.meetingos.publicapi;

import com.meetingos.common.BusinessException;
import com.meetingos.security.PublicQueryProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicRateLimitService {

    private final PublicQueryProperties properties;
    private final Map<String, Deque<Long>> hitsByClient = new ConcurrentHashMap<>();

    public PublicRateLimitService(PublicQueryProperties properties) {
        this.properties = properties;
    }

    public void check(HttpServletRequest request) {
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        String client = resolveClientIp(request);
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60_000;
        Deque<Long> hits = hitsByClient.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.removeFirst();
            }
            if (hits.size() >= limit) {
                throw BusinessException.badRequest("公开查询过于频繁，请稍后再试");
            }
            hits.addLast(now);
        }
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        /*
         * 只有在明确处于可信反向代理之后时才接受转发头。
         * 如果公网可以直连后端，客户端可随意伪造 X-Forwarded-For 绕过公开查询限流和审计。
         */
        if (properties.isTrustForwardHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
