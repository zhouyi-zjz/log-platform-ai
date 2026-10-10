package com.zhouyi.admin.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 项目采集 Token 的持久化入口。
 *
 * <p>数据库只保存不可逆的 SHA-256 摘要，原始 Token 只在签发响应中出现一次，
 * 避免数据库读取权限泄露后直接获得采集凭据。</p>
 */
@Repository
public class ProjectTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 查询启用项目的 Token 摘要。 */
    public Optional<String> findEnabledTokenHash(String projectId) {
        try {
            String hash = jdbcTemplate.queryForObject(
                    "SELECT ingest_token_hash FROM log_project WHERE project_id = ? AND enabled = 1",
                    String.class, projectId);
            return Optional.ofNullable(hash).filter(value -> !value.isBlank());
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    /** 将新 Token 摘要写入指定项目，并返回项目是否存在且处于启用状态。 */
    public boolean updateTokenHash(String projectId, String tokenHash) {
        return jdbcTemplate.update(
                "UPDATE log_project SET ingest_token_hash = ? WHERE project_id = ? AND enabled = 1",
                tokenHash, projectId) == 1;
    }
}
