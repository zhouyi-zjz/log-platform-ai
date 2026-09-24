package com.zhouyi.admin.service;

import com.zhouyi.admin.config.LogQueryProperties;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 只覆盖权限窗口的核心边界，ES 查询本身通过部署联调验证。 */
class LogQueryWindowServiceTest {

    private final LogQueryWindowService service = new LogQueryWindowService(new LogQueryProperties());

    @Test
    void normalUserWindowStartsAtBusinessDay() {
        var window = service.resolve(null, null, false);
        var expected = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .toLocalDate().atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        assertThat(window.from()).isEqualTo(expected);
        assertThat(window.to()).isAfter(window.from());
    }

    @Test
    void oldNormalUserRangeIsRejected() {
        assertThatThrownBy(() -> service.resolve("2020-01-01T00:00:00Z", "2020-01-01T01:00:00Z", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("普通用户只能查询当天日志");
    }

    @Test
    void adminRangeIsCappedAtSevenDays() {
        var window = service.resolve(null, null, true);
        assertThat(Duration.between(window.permissionFrom(), window.permissionTo()).getSeconds())
                .isEqualTo(168 * 3600L);
    }
}
