package com.zhouyi.admin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.MapSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话存储开关配置。
 *
 * <p>Spring Boot 3.5 在 Redis 依赖存在时会默认启用 Redis Session，
 * 不再把 {@code spring.session.store-type=none} 作为可靠的禁用开关。
 * 这里显式提供内存仓库，避免本地开发和测试在没有 Redis 时失败；生产多副本
 * 部署应设置 {@code spring.session.store-type=redis} 使用共享会话。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none", matchIfMissing = true)
public class SessionStoreConfiguration {

    /**
     * 创建进程内会话仓库，适用于单实例开发、测试和不需要跨副本会话的场景。
     *
     * @return 基于并发 Map 的 Spring Session 仓库
     */
    @Bean
    public SessionRepository<MapSession> inMemorySessionRepository() {
        return new MapSessionRepository(new ConcurrentHashMap<>());
    }
}
