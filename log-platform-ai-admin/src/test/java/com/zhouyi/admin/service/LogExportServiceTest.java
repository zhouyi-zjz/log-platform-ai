package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouyi.admin.config.LogQueryProperties;
import com.zhouyi.admin.domain.log.LogExportStatus;
import com.zhouyi.admin.domain.log.LogExportStatusResponse;
import com.zhouyi.admin.domain.log.LogSearchRequest;
import com.zhouyi.admin.domain.log.LogSearchResponse;
import com.zhouyi.admin.domain.log.ProjectAccessScope;
import com.zhouyi.common.domain.entity.SysUser;
import com.zhouyi.common.domain.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 异步导出服务的分页、CSV 转义和任务所有权测试。 */
class LogExportServiceTest {

    private final LogQueryService queryService = mock(LogQueryService.class);
    private final LogQueryProperties properties = properties();
    private final LogExportService service = new LogExportService(
            queryService, properties, new ObjectMapper(), null);

    @AfterEach
    void shutdownWorker() {
        service.shutdown();
    }

    /** 验证导出会继续使用查询服务分页，并阻止 CSV 公式注入。 */
    @Test
    void exportsEscapedCsvAndPaginates() throws Exception {
        ProjectAccessScope scope = ProjectAccessScope.administrator();
        ObjectNode firstSource = new ObjectMapper().createObjectNode()
                .put("@timestamp", "2026-10-05T00:00:00Z")
                .put("event.id", "event-1")
                .put("project.id", "billing")
                .put("message", "=SUM(1,2),\nnext-line");
        ObjectNode secondSource = firstSource.deepCopy().put("event.id", "event-2").put("message", "normal");
        when(queryService.search(any(), eq(scope))).thenAnswer(invocation -> {
            LogSearchRequest request = invocation.getArgument(0);
            if (request.searchAfter() == null || request.searchAfter().isEmpty()) {
                return response(new LogSearchResponse.LogHit("doc-1", "logs-1", firstSource, List.of("t1", "event-1")),
                        List.of("t1", "event-1"));
            }
            return response(new LogSearchResponse.LogHit("doc-2", "logs-1", secondSource, List.of("t2", "event-2")),
                    List.of());
        });

        LogExportStatusResponse created = service.submit(query(), scope, adminAuthentication());
        LogExportStatusResponse completed = awaitStatus(created.jobId(), adminAuthentication());

        assertThat(completed.status()).isEqualTo(LogExportStatus.SUCCEEDED);
        assertThat(completed.rowCount()).isEqualTo(2);
        String csv = new String(service.download(created.jobId(), adminAuthentication()).content());
        assertThat(csv).contains("'=SUM(1,2),\nnext-line").contains("doc-2");
    }

    /** 验证普通用户不能读取其他用户创建的导出任务。 */
    @Test
    void rejectsDownloadByAnotherUser() {
        when(queryService.search(any(), any())).thenReturn(new LogSearchResponse(
                List.of(), List.of(), 0, Instant.now().minusSeconds(1), Instant.now()));
        LogExportStatusResponse created = service.submit(query(), ProjectAccessScope.administrator(),
                adminAuthentication());

        assertThatThrownBy(() -> service.status(created.jobId(), userAuthentication()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private LogExportStatusResponse awaitStatus(String jobId, Authentication authentication) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            LogExportStatusResponse status = service.status(jobId, authentication);
            if (status.status() != LogExportStatus.QUEUED && status.status() != LogExportStatus.RUNNING) {
                return status;
            }
            Thread.sleep(25);
        }
        return service.status(jobId, authentication);
    }

    private LogSearchRequest query() {
        return new LogSearchRequest("2026-10-05T00:00:00Z", "2026-10-05T01:00:00Z",
                "billing", null, null, null, null, null, null, null, null, Map.of(), 2, null);
    }

    private LogSearchResponse response(LogSearchResponse.LogHit hit, List<Object> cursor) {
        return new LogSearchResponse(List.of(hit), cursor, 2,
                Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-05T01:00:00Z"));
    }

    private LogQueryProperties properties() {
        LogQueryProperties value = new LogQueryProperties();
        value.setMaxPageSize(50);
        value.setExportMaxRows(10);
        value.setExportMaxBytes(100000);
        value.setExportWorkerThreads(1);
        value.setExportQueueCapacity(2);
        value.setExportRetentionMinutes(30);
        return value;
    }

    private Authentication adminAuthentication() {
        LoginUser user = new LoginUser(new SysUser(1L, "admin", "hash", "管理员", true, true));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Authentication userAuthentication() {
        LoginUser user = new LoginUser(new SysUser(2L, "viewer", "hash", "查看者", false, true));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
