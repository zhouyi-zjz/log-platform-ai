package com.zhouyi.admin.service;

import com.zhouyi.admin.config.LogQueryProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 *  <p>权限边界必须在后端执行，不能依赖前端传参：普通用户只能看当前自然日，
 *  管理员只能看最近配置的小时数。所有搜索、详情上下文和聚合都复用本服务。</p>
 *
 * @author zjz
 * @version 1.0
 * @description: 统一计算日志查询的有效时间窗口
 * @date 2026/9/12
 */
@Service
public class LogQueryWindowService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final LogQueryProperties properties;

    public LogQueryWindowService(LogQueryProperties properties) {
        this.properties = properties;
    }

    public QueryWindow resolve(String fromText, String toText, boolean admin) {
        Instant now = Instant.now();
        Instant permissionFrom = admin
                ? now.minus(Duration.ofHours(properties.getAdminMaxHours()))
                : ZonedDateTime.now(BUSINESS_ZONE).toLocalDate().atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant permissionTo = now;

        Instant requestedFrom = parseOrDefault(fromText, permissionFrom, "from");
        Instant requestedTo = parseOrDefault(toText, permissionTo, "to");
        if (!requestedFrom.isBefore(requestedTo)) {
            throw new IllegalArgumentException("from 必须早于 to");
        }
        // 请求范围与权限范围没有交集时直接拒绝，而不是返回一个容易误解的空结果。
        if (!requestedTo.isAfter(permissionFrom) || !requestedFrom.isBefore(permissionTo)) {
            throw new IllegalArgumentException(admin
                    ? "管理员最多查询最近 " + properties.getAdminMaxHours() + " 小时"
                    : "普通用户只能查询当天日志");
        }
        Instant effectiveFrom = requestedFrom.isAfter(permissionFrom) ? requestedFrom : permissionFrom;
        Instant effectiveTo = requestedTo.isBefore(permissionTo) ? requestedTo : permissionTo;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("有效查询时间范围为空");
        }
        return new QueryWindow(effectiveFrom, effectiveTo, permissionFrom, permissionTo);
    }

    public Instant parseTimestamp(String value) {
        return parseOrDefault(value, Instant.now(), "timestamp");
    }

    private static Instant parseOrDefault(String value, Instant fallback, String field) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignored) {
                throw new IllegalArgumentException(field + " 必须使用 ISO-8601 时间格式", exception);
            }
        }
    }

    public record QueryWindow(Instant from, Instant to, Instant permissionFrom, Instant permissionTo) {
    }
}
