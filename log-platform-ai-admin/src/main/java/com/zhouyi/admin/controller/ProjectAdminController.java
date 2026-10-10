package com.zhouyi.admin.controller;

import com.zhouyi.admin.service.ProjectTokenService;
import com.zhouyi.common.domain.AjaxResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员项目配置接口，当前提供采集 Token 轮换能力。 */
@RestController
@RequestMapping("/monitor/api/admin/projects")
public class ProjectAdminController {

    private final ProjectTokenService projectTokenService;

    public ProjectAdminController(ProjectTokenService projectTokenService) {
        this.projectTokenService = projectTokenService;
    }

    /** 轮换项目采集 Token；旧 Token 从返回成功时立即失效。 */
    @PostMapping("/{projectId}/ingest-token/rotate")
    public ResponseEntity<AjaxResult<TokenView>> rotateToken(@PathVariable String projectId) {
        try {
            return ResponseEntity.ok(AjaxResult.success(new TokenView(projectTokenService.rotate(projectId))));
        } catch (ProjectTokenService.ProjectNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AjaxResult.error(HttpStatus.NOT_FOUND.value(), exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(AjaxResult.error(HttpStatus.BAD_REQUEST.value(), exception.getMessage()));
        }
    }

    /** Token 响应模型，防止把内部数据库字段暴露给 API 调用方。 */
    public record TokenView(
            /** 只在轮换响应中返回一次的项目采集 Token。 */
            String token) {
    }
}
