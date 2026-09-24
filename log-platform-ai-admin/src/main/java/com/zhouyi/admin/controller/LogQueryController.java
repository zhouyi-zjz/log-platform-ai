package com.zhouyi.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhouyi.admin.domain.log.LogAggregationRequest;
import com.zhouyi.admin.domain.log.LogContextRequest;
import com.zhouyi.admin.domain.log.LogSearchRequest;
import com.zhouyi.admin.domain.log.LogSearchResponse;
import com.zhouyi.admin.service.LogQueryLimiter;
import com.zhouyi.admin.service.LogQueryService;
import com.zhouyi.common.domain.AjaxResult;
import com.zhouyi.common.domain.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

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

    public LogQueryController(LogQueryService queryService, LogQueryLimiter limiter) {
        this.queryService = queryService;
        this.limiter = limiter;
    }

    @PostMapping("/search")
    public ResponseEntity<AjaxResult<LogSearchResponse>> search(
            @RequestBody(required = false) LogSearchRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(queryService.search(request, isAdmin(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), "日志查询超时，请缩小时间范围或稍后重试"));
        } catch (RestClientResponseException exception) {
            return esError(exception);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<AjaxResult<LogSearchResponse.LogHit>> detail(
            @PathVariable String id, Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(queryService.detail(id, isAdmin(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AjaxResult.error(404, exception.getMessage()));
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), "日志查询超时"));
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
                    id, request, isAdmin(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
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
            return ResponseEntity.ok(AjaxResult.success(queryService.aggregate(request, isAdmin(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return tooManyRequests(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(HttpStatus.GATEWAY_TIMEOUT.value(), "聚合查询超时"));
        } catch (RestClientResponseException exception) {
            return esError(exception);
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof LoginUser user && user.isAdmin();
    }

    private static <T> ResponseEntity<AjaxResult<T>> tooManyRequests(String message) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(AjaxResult.error(HttpStatus.TOO_MANY_REQUESTS.value(), message));
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
