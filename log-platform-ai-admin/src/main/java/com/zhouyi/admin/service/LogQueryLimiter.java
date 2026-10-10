package com.zhouyi.admin.service;

import com.zhouyi.admin.config.LogQueryProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
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
    private final StringRedisTemplate redisTemplate;

    public LogQueryLimiter(LogQueryProperties properties) {
        this(properties, (StringRedisTemplate) null);
    }

    /** 创建支持 Redis 共享计数的查询保护器；未启用时完全保持本地实现。 */
    @org.springframework.beans.factory.annotation.Autowired
    public LogQueryLimiter(LogQueryProperties properties, ObjectProvider<StringRedisTemplate> redisProvider) {
        this(properties, redisProvider == null ? null : redisProvider.getIfAvailable());
    }

    private LogQueryLimiter(LogQueryProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.concurrent = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
        this.redisTemplate = redisTemplate;
    }

    /** 获取查询并发许可和用户/IP 频率许可。 */
    public Permit enter(Authentication authentication, HttpServletRequest request) {
        String username = authentication == null ? "anonymous" : authentication.getName();
        String remote = request == null ? "unknown" : request.getRemoteAddr();
        String key = username + "@" + remote;
        long minute = Instant.now().getEpochSecond() / 60;
        if (properties.isDistributedRateLimit() && redisTemplate != null) {
            if (!tryRedisRateLimit(key, minute)) {
                throw new QueryLimitException("查询频率过高，请稍后再试");
            }
        } else {
            evictExpired(minute);
            counters.compute(key, (ignored, old) -> {
                if (old == null || old.minute != minute) {
                    return new Counter(minute, 1);
                }
                if (old.count >= properties.getRateLimitPerMinute()) {
                    throw new QueryLimitException("查询频率过高，请稍后再试");
                }
                old.count++;
                return old;
            });
        }
        if (!concurrent.tryAcquire()) {
            throw new QueryLimitException("当前查询繁忙，请稍后再试");
        }
        return new Permit(concurrent);
    }

    /** 使用 Redis INCR + EXPIRE 实现跨实例分钟窗口，Redis 异常时 fail closed。 */
    private boolean tryRedisRateLimit(String identity, long minute) {
        try {
            String redisKey = "log-platform:query-limit:" + digest(identity + ":" + minute);
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, 70, TimeUnit.SECONDS);
            }
            return count != null && count <= Math.max(1, properties.getRateLimitPerMinute());
        } catch (RuntimeException exception) {
            throw new QueryLimitException("查询限流服务暂不可用，请稍后重试");
        }
    }

    /** 对用户名和 IP 做摘要，避免把身份信息直接写入 Redis key。 */
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    /** 按需淘汰两个分钟窗口以前的计数，避免长期运行的用户/IP 组合无限增长。 */
    private void evictExpired(long currentMinute) {
        if (counters.size() < 1024) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().minute < currentMinute - 1);
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
