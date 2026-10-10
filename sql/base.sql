CREATE DATABASE IF NOT EXISTS `log-platform`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `log-platform`;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(60) NOT NULL COMMENT 'BCrypt密码哈希',
    display_name VARCHAR(64) NOT NULL COMMENT '显示名称',
    is_admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为管理员',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许登录',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB COMMENT='日志平台用户';

CREATE TABLE IF NOT EXISTS log_project (
    project_id VARCHAR(64) NOT NULL COMMENT '日志中的 project.id',
    project_name VARCHAR(128) NOT NULL COMMENT '项目显示名称',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许查询',
    ingest_token_hash CHAR(64) NULL COMMENT '项目采集 Token 的 SHA-256 摘要，不保存明文',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id)
) ENGINE=InnoDB COMMENT='日志项目';

CREATE TABLE IF NOT EXISTS sys_user_project (
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    project_id VARCHAR(64) NOT NULL COMMENT '项目ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, project_id),
    CONSTRAINT fk_user_project_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_project_project FOREIGN KEY (project_id) REFERENCES log_project (project_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='用户项目权限';

CREATE TABLE IF NOT EXISTS log_query_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审计记录ID',
    username VARCHAR(64) NOT NULL COMMENT '发起查询的用户名',
    operation VARCHAR(32) NOT NULL COMMENT '操作类型',
    outcome VARCHAR(16) NOT NULL COMMENT '操作结果',
    project_id VARCHAR(64) NULL COMMENT '请求指定的项目',
    duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '服务端耗时毫秒',
    detail VARCHAR(512) NULL COMMENT '受限长度的结果说明，不保存日志正文',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计时间',
    PRIMARY KEY (id),
    KEY idx_log_query_audit_created_at (created_at),
    KEY idx_log_query_audit_username (username)
) ENGINE=InnoDB COMMENT='日志查询审计';

-- 管理员由 BOOTSTRAP_ADMIN_* Secret 在应用启动时创建或同步，SQL 中不保存默认密码。
