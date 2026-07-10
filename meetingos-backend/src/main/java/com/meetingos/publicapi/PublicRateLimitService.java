package com.meetingos.publicapi;

import com.meetingos.common.BusinessException;
import com.meetingos.security.ClientIpResolver;
import com.meetingos.security.PublicQueryProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PublicRateLimitService {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final long ENTRY_TTL_MILLIS = 2 * WINDOW_MILLIS;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final PublicQueryProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final Map<String, ClientHits> hitsByClient = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupAt = new AtomicLong();
    private final Object capacityLock = new Object();
    private final int maxTrackedClients;

    @Autowired
    public PublicRateLimitService(PublicQueryProperties properties, ClientIpResolver clientIpResolver) {
        this(properties, clientIpResolver, MAX_TRACKED_CLIENTS);
    }

    PublicRateLimitService(PublicQueryProperties properties, ClientIpResolver clientIpResolver, int maxTrackedClients) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.maxTrackedClients = Math.max(1, maxTrackedClients);
    }

    public void check(HttpServletRequest request) {
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        String client = resolveClientIp(request);
        long now = Instant.now().toEpochMilli();
        cleanupIfNeeded(now);
        long windowStart = now - WINDOW_MILLIS;
        ClientHits clientHits = getOrCreate(client, now);
        if (clientHits == null) {
            throw BusinessException.badRequest("公开查询过于频繁，请稍后再试");
        }
        Deque<Long> hits = clientHits.hits;
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.removeFirst();
            }
            if (hits.size() >= limit) {
                throw BusinessException.badRequest("公开查询过于频繁，请稍后再试");
            }
            hits.addLast(now);
            clientHits.lastTouchedAt = now;
        }
    }

    public String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private ClientHits getOrCreate(String client, long now) {
        ClientHits existing = hitsByClient.get(client);
        if (existing != null) {
            return existing;
        }
        synchronized (capacityLock) {
            existing = hitsByClient.get(client);
            if (existing != null) {
                return existing;
            }
            if (hitsByClient.size() >= maxTrackedClients) {
                cleanupExpiredEntries(now);
            }
            if (hitsByClient.size() >= maxTrackedClients) {
                return null;
            }
            ClientHits created = new ClientHits(now);
            hitsByClient.put(client, created);
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
        hitsByClient.forEach((client, hits) -> {
            synchronized (hits.hits) {
                if (now - hits.lastTouchedAt >= ENTRY_TTL_MILLIS) {
                    hitsByClient.remove(client, hits);
                }
            }
        });
    }

    int trackedClientCount() {
        return hitsByClient.size();
    }

    private static final class ClientHits {
        private final Deque<Long> hits = new ArrayDeque<>();
        private volatile long lastTouchedAt;

        private ClientHits(long now) {
            this.lastTouchedAt = now;
        }
    }
}
