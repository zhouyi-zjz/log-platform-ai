-- 已有环境执行一次：为项目接入凭据和查询审计补充结构。
ALTER TABLE log_project
    ADD COLUMN IF NOT EXISTS ingest_token_hash CHAR(64) NULL
    COMMENT '项目采集 Token 的 SHA-256 摘要，不保存明文';

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
