package com.zhouyi.admin.repository;

import com.zhouyi.common.domain.entity.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
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

    /** 按登录名加载启用状态、角色和 BCrypt 哈希。 */
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

    /** 幂等创建或同步启动管理员，避免 SQL 文件携带固定密码。 */
    public boolean upsertAdmin(String username, String passwordHash, String displayName) {
        int updated = jdbcTemplate.update("""
                        UPDATE sys_user
                        SET password_hash = ?, display_name = ?, is_admin = 1, enabled = 1
                        WHERE username = ?
                        """, passwordHash, displayName, username);
        if (updated == 1) {
            return false;
        }
        try {
            jdbcTemplate.update("""
                            INSERT INTO sys_user (username, password_hash, display_name, is_admin, enabled)
                            VALUES (?, ?, ?, 1, 1)
                            """, username, passwordHash, displayName);
            return true;
        } catch (DuplicateKeyException ignored) {
            jdbcTemplate.update("""
                            UPDATE sys_user
                            SET password_hash = ?, display_name = ?, is_admin = 1, enabled = 1
                            WHERE username = ?
                            """, passwordHash, displayName, username);
            return false;
        }
    }
}
