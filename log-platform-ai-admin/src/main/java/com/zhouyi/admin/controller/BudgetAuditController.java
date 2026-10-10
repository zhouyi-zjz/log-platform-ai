package com.zhouyi.admin.controller;

import com.zhouyi.admin.domain.log.BudgetAuditSearchRequest;
import com.zhouyi.admin.domain.log.BudgetAuditSearchResponse;
import com.zhouyi.admin.service.BudgetAuditQueryService;
import com.zhouyi.admin.service.LogQueryLimiter;
import com.zhouyi.admin.service.ProjectAuthorizationService;
import com.zhouyi.common.domain.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** FinOps 预算审计的只读查询 API；不提供成本计算、账本写入或预算裁决接口。 */
@RestController
@RequestMapping("/monitor/api/finops/budget-audit")
public class BudgetAuditController {

    private final BudgetAuditQueryService queryService;
    private final LogQueryLimiter limiter;
    private final ProjectAuthorizationService authorizationService;

    /** 创建预算审计查询控制器。 */
    public BudgetAuditController(BudgetAuditQueryService queryService, LogQueryLimiter limiter,
                                 ProjectAuthorizationService authorizationService) {
        this.queryService = queryService;
        this.limiter = limiter;
        this.authorizationService = authorizationService;
    }

    /** 查询预算裁决和 Reservation 审计事件，权限范围取自服务端登录态。 */
    @PostMapping("/search")
    public ResponseEntity<AjaxResult<BudgetAuditSearchResponse>> search(
            @RequestBody(required = false) BudgetAuditSearchRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        try (LogQueryLimiter.Permit ignored = limiter.enter(authentication, servletRequest)) {
            return ResponseEntity.ok(AjaxResult.success(queryService.search(
                    request, authorizationService.resolve(authentication))));
        } catch (LogQueryLimiter.QueryLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(AjaxResult.error(429, exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, exception.getMessage()));
        } catch (AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(AjaxResult.error(403, exception.getMessage()));
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(AjaxResult.error(504, "预算审计查询超时"));
        } catch (RestClientResponseException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(AjaxResult.error(exception.getStatusCode().value(), "预算审计暂时不可用"));
        }
    }
}
