package com.zhouyi.admin.domain.log;

/** 异步日志导出任务状态。 */
public enum LogExportStatus {
    /** 已进入有界等待队列。 */
    QUEUED,
    /** 正在从受控查询服务分页读取日志。 */
    RUNNING,
    /** CSV 已生成，可以下载。 */
    SUCCEEDED,
    /** 导出过程中发生错误。 */
    FAILED,
    /** 结果超过保留时间，不能再下载。 */
    EXPIRED
}
