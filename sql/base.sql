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

-- 初始密码仅用于首次登录，部署后应立即修改。
-- admin  / admin@2024
-- viewer / viewer@2024
INSERT INTO sys_user (username, password_hash, display_name, is_admin, enabled)
VALUES
    ('admin', '$2a$12$gWbnQ5VembKCJh1W1mEd0eaOGa/pwyUPw3Izrt1Iozc31y.uayq0m', '系统管理员', 1, 1),
    ('viewer', '$2a$12$zk2aHgKG3xo.uiz3Wa6GK.Mcl1pOG0VV5uqjU.nitKfyA3txWRg9e', '日志查询用户', 0, 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    display_name = VALUES(display_name),
    is_admin = VALUES(is_admin),
    enabled = VALUES(enabled);
