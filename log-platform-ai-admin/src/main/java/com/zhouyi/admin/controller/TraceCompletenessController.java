package com.zhouyi.admin.controller;

import com.zhouyi.admin.service.TraceCompletenessService;
import com.zhouyi.common.domain.AjaxResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员 Trace 完整率巡检接口。 */
@RestController
@RequestMapping("/monitor/api/admin/observability")
public class TraceCompletenessController {

    private final TraceCompletenessService completenessService;

    /** 创建 Trace 完整率控制器。 */
    public TraceCompletenessController(TraceCompletenessService completenessService) {
        this.completenessService = completenessService;
    }

    /** 返回当前实例的 Trace 关联完整率快照，不暴露单条日志内容。 */
    @GetMapping("/trace-completeness")
    public AjaxResult<TraceCompletenessService.Snapshot> snapshot() {
        return AjaxResult.success(completenessService.snapshot());
    }
}
