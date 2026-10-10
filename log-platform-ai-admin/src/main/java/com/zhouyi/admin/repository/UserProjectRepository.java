package com.zhouyi.admin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class UserProjectRepository {

    private static final String SELECT_PROJECT_IDS = """
            SELECT up.project_id
            FROM sys_user_project up
            JOIN log_project p ON p.project_id = up.project_id
            WHERE up.user_id = ? AND p.enabled = 1
            ORDER BY up.project_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 查询用户被授予且项目仍启用的项目 ID。 */
    public Set<String> findEnabledProjectIdsByUserId(Long userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(SELECT_PROJECT_IDS, String.class, userId));
    }
}
