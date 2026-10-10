package com.zhouyi.admin.controller;

import com.zhouyi.admin.domain.log.DltReplayRequest;
import com.zhouyi.admin.domain.log.DltTopicStatus;
import com.zhouyi.admin.service.DltReplayService;
import com.zhouyi.common.domain.AjaxResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员 DLT 查看与单条安全重放 API。 */
@RestController
@RequestMapping("/monitor/api/admin/dlt")
public class DltAdminController {

    private final DltReplayService service;

    /** 创建 DLT 控制器。 */
    public DltAdminController(DltReplayService service) {
        this.service = service;
    }

    /** 返回 DLT 分区积压快照，不包含事件正文。 */
    @GetMapping("/topics")
    public AjaxResult<List<DltTopicStatus>> statuses() {
        return AjaxResult.success(service.statuses());
    }

    /** 重放一条经过固定 Topic 校验的 DLT 消息。 */
    @PostMapping("/replay")
    public ResponseEntity<AjaxResult<DltReplayService.ReplayResult>> replay(
            @RequestBody DltReplayRequest request, Authentication authentication) {
        try {
            return ResponseEntity.ok(AjaxResult.success(service.replay(request, authentication)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError().body(AjaxResult.error(500, "DLT 重放失败"));
        }
    }
}
