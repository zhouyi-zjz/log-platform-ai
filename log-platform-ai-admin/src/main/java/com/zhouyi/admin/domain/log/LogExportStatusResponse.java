package com.zhouyi.admin.domain.log;

import java.time.Instant;

/** 异步导出任务对外状态，不暴露线程、异常堆栈或权限内部信息。 */
public record LogExportStatusResponse(
        /** 导出任务 ID。 */
        String jobId,
        /** 当前任务状态。 */
        LogExportStatus status,
        /** 创建任务的用户名。 */
        String owner,
        /** 已生成的日志行数。 */
        long rowCount,
        /** 任务创建时间。 */
        Instant createdAt,
        /** 开始执行时间。 */
        Instant startedAt,
        /** 完成或失败时间。 */
        Instant finishedAt,
        /** 结果过期时间。 */
        Instant expiresAt,
        /** 失败时的用户可读原因。 */
        String error,
        /** 成功任务的下载接口相对路径。 */
        String downloadUrl) {
}
