CREATE TABLE sys_user (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          username VARCHAR(50) NOT NULL UNIQUE,
                          password VARCHAR(255) NOT NULL,
                          enabled TINYINT(1) NOT NULL DEFAULT 1,
                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_user_role (
                               user_id BIGINT NOT NULL,
                               role VARCHAR(50) NOT NULL,
                               FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

-- 初始管理员密码为 admin@2024 的 BCrypt 加密结果
INSERT INTO sys_user (username, password) VALUES
    ('admin', '$2a$12$...BCryptHash...');
INSERT INTO sys_user_role (user_id, role) VALUES (1, 'ADMIN');