package com.zhouyi.admin.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** 记录日志查询的最小审计信息，不保存原始关键字和日志正文，降低敏感信息扩散。 */
@Service
public class LogQueryAuditService {

    private final JdbcTemplate jdbcTemplate;

    public LogQueryAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 写入一次查询审计；底层失败时向调用方抛出，由查询服务决定是否降级。 */
    public void record(Authentication authentication, String operation, String outcome,
                       String projectId, Instant startedAt, String detail) {
        String username = authentication == null ? "anonymous" : authentication.getName();
        long duration = startedAt == null ? 0 : Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
        jdbcTemplate.update("""
                INSERT INTO log_query_audit
                    (username, operation, outcome, project_id, duration_ms, detail)
                VALUES (?, ?, ?, ?, ?, ?)
                """, username, operation, outcome, projectId, duration, trim(detail));
    }

    /** 限制审计备注长度，避免异常消息把控制面数据库撑大。 */
    private static String trim(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
