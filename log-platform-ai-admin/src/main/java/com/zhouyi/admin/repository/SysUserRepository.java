package com.zhouyi.admin.repository;

import com.zhouyi.common.domain.entity.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SysUserRepository {

    private static final String SELECT_BY_USERNAME = """
            SELECT id, username, password_hash, display_name, is_admin, enabled
            FROM sys_user
            WHERE username = ?
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public SysUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SysUser> findByUsername(String username) {
        return jdbcTemplate.query(
                        SELECT_BY_USERNAME,
                        (resultSet, rowNum) -> new SysUser(
                                resultSet.getLong("id"),
                                resultSet.getString("username"),
                                resultSet.getString("password_hash"),
                                resultSet.getString("display_name"),
                                resultSet.getBoolean("is_admin"),
                                resultSet.getBoolean("enabled")),
                        username)
                .stream()
                .findFirst();
    }
}
