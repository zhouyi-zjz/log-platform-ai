package com.zhouyi.admin.service;

import com.zhouyi.admin.config.LogQueryProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * <p>这是单实例内存限流，适合作为第一版保护措施。多实例部署时应替换为 Redis
 * 或网关限流，否则每个实例都会拥有独立配额。</p>
 *
 * @author zjz
 * @version 1.0
 * @description: 查询侧保护器：限制单用户/IP 的分钟请求数，并限制全局并行查询数。
 * @date 2026/9/12
 */
@Component
public class LogQueryLimiter {

    private final LogQueryProperties properties;
    private final Semaphore concurrent;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public LogQueryLimiter(LogQueryProperties properties) {
        this.properties = properties;
        this.concurrent = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
    }

    public Permit enter(Authentication authentication, HttpServletRequest request) {
        String username = authentication == null ? "anonymous" : authentication.getName();
        String remote = request == null ? "unknown" : request.getRemoteAddr();
        String key = username + "@" + remote;
        long minute = Instant.now().getEpochSecond() / 60;
        Counter counter = counters.compute(key, (ignored, old) -> {
            if (old == null || old.minute != minute) {
                return new Counter(minute, 1);
            }
            if (old.count >= properties.getRateLimitPerMinute()) {
                throw new QueryLimitException("查询频率过高，请稍后再试");
            }
            old.count++;
            return old;
        });
        if (!concurrent.tryAcquire()) {
            throw new QueryLimitException("当前查询繁忙，请稍后再试");
        }
        return new Permit(concurrent);
    }

    private static final class Counter {
        private final long minute;
        private int count;

        private Counter(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) { this.semaphore = semaphore; }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                semaphore.release();
            }
        }
    }

    public static class QueryLimitException extends RuntimeException {
        public QueryLimitException(String message) { super(message); }
    }
}
