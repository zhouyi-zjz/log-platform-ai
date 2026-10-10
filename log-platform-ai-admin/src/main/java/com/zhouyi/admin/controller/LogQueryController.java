package com.zhouyi.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhouyi.admin.domain.log.LogAggregationRequest;
import com.zhouyi.admin.domain.log.LogContextRequest;
import com.zhouyi.admin.domain.log.LogExportStatusResponse;
import com.zhouyi.admin.domain.log.LogSearchRequest;
import com.zhouyi.admin.domain.log.LogSearchResponse;
import com.zhouyi.admin.service.LogExportService;
import com.zhouyi.admin.service.LogQueryLimiter;
import com.zhouyi.admin.service.LogQueryService;
import com.zhouyi.admin.service.ProjectAuthorizationService;
import com.zhouyi.common.domain.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

/**
 * 日志查询 API。
 *
 * <p>所有接口都要求登录，管理员身份只取自服务端登录态中的 isAdmin，
 * 不接受请求体里的 admin 字段。每次调用均经过统一限流器。</p>
 */
@RestController
@RequestMapping("/monitor/api/logs")
public class LogQueryController {

    private final LogQueryService queryService;
    private final LogQueryLimiter limiter;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final LogExportService exportService;

    public LogQueryController(LogQueryService queryService, LogQueryLimiter limiter,
                              ProjectAuthorizationService projectAuthorizationService,
                              LogExportService exportService) {
        this.queryService = queryService;
        this.limiter = limiter;
        this.projectAuthorizationService = projectAuthorizationService;
        this.exportService = exportService;
    }

    @PostMapping("/search")
    public ResponseEntity<AjaxResult<LogSearchResponse>> search(
            @RequestBody(required = false) LogSearchRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(queryService.search(
                    request, projectAuthorizationService.resolve(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return forbidden(exception);
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), "日志查询超时，请缩小时间范围或稍后重试"));
        } catch (LogQueryService.QueryTimeoutException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), exception.getMessage()));
        } catch (RestClientResponseException exception) {
            return esError(exception);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<AjaxResult<LogSearchResponse.LogHit>> detail(
            @PathVariable String id, Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(queryService.detail(
                    id, projectAuthorizationService.resolve(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AjaxResult.error(404, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return forbidden(exception);
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), "日志查询超时"));
        } catch (LogQueryService.QueryTimeoutException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), exception.getMessage()));
        } catch (RestClientResponseException exception) {
            return esError(exception);
        }
    }

    @PostMapping("/{id}/context")
    public ResponseEntity<AjaxResult<LogSearchResponse>> context(
            @PathVariable String id, @RequestBody(required = false) LogContextRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(queryService.context(
                    id, request, projectAuthorizationService.resolve(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return forbidden(exception);
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), "上下文查询超时"));
        } catch (RestClientResponseException exception) {
            return esError(exception);
        }
    }

    @PostMapping("/aggregations")
    public ResponseEntity<AjaxResult<JsonNode>> aggregations(
            @RequestBody LogAggregationRequest request, Authentication authentication,
            HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(queryService.aggregate(
                    request, projectAuthorizationService.resolve(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return forbidden(exception);
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), "聚合查询超时"));
        } catch (LogQueryService.QueryTimeoutException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), exception.getMessage()));
        } catch (RestClientResponseException exception) {
            return esError(exception);
        }
    }

    /** 创建异步 CSV 导出任务，查询条件与普通日志搜索接口保持一致。 */
    @PostMapping("/exports")
    public ResponseEntity<AjaxResult<LogExportStatusResponse>> createExport(
            @RequestBody(required = false) LogSearchRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(AjaxResult.success(
                    "导出任务已创建", exportService.submit(request,
                            projectAuthorizationService.resolve(authentication), authentication)));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (LogExportService.ExportBusyException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return forbidden(exception);
        }
    }

    /** 查询导出任务状态，普通用户只能查看自己创建的任务。 */
    @GetMapping("/exports/{jobId}")
    public ResponseEntity<AjaxResult<LogExportStatusResponse>> exportStatus(
            @PathVariable String jobId, Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(exportService.status(jobId, authentication)));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (LogExportService.ExportNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AjaxResult.error(404, exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return forbidden(exception);
        }
    }

    /** 下载已经成功生成的 CSV 文件，并再次执行任务所有权校验。 */
    @GetMapping("/exports/{jobId}/download")
    public ResponseEntity<?> downloadExport(
            @PathVariable String jobId, Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            LogExportService.ExportDownload download = exportService.download(jobId, authentication);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(download.filename(), StandardCharsets.UTF_8).build());
            return new ResponseEntity<>(download.content(), headers, HttpStatus.OK);
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (LogExportService.ExportNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AjaxResult.error(404, exception.getMessage()));
        } catch (LogExportService.ExportExpiredException exception) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(AjaxResult.error(HttpStatus.GONE.value(), exception.getMessage()));
        } catch (LogExportService.ExportNotReadyException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(AjaxResult.error(HttpStatus.CONFLICT.value(), exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return forbidden(exception);
        }
    }

    private static <T> ResponseEntity<AjaxResult<T>> tooManyRequests(String message) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(AjaxResult.error(HttpStatus.TOO_MANY_REQUESTS.value(), message));
    }

    private static <T> ResponseEntity<AjaxResult<T>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AjaxResult.error(HttpStatus.FORBIDDEN.value(), exception.getMessage()));
    }

    private static <T> ResponseEntity<AjaxResult<T>> esError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 408 || status == 429 || status >= 500) {
            status = status == 408 ? HttpStatus.GATEWAY_TIMEOUT.value() : status;
        }
        return ResponseEntity.status(status)
                .body(AjaxResult.error(status, "Elasticsearch 暂时无法完成查询"));
    }
}
