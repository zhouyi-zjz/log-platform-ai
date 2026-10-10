package com.zhouyi.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.service.DatabaseUserDetailsService;
import com.zhouyi.common.domain.AjaxResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        // Fluent Bit/服务端采集器没有浏览器 Cookie，不应被 CSRF 机制拦截；
                        // 采集接口使用独立 Token（X-Log-Ingest-Token）保护。
                        .ignoringRequestMatchers("/monitor/api/logs/ingest"))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/monitor/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/monitor/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/monitor/api/logs/ingest").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Prometheus 使用无状态抓取；生产通过 NetworkPolicy 只允许监控命名空间访问该端点。
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/monitor/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/monitor/**").authenticated()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/monitor/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(response, objectMapper, HttpServletResponse.SC_OK,
                                        AjaxResult.success("退出成功", null))))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, cause) ->
                                writeJson(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                        AjaxResult.error(HttpServletResponse.SC_UNAUTHORIZED, "请先登录")))
                        .accessDeniedHandler((request, response, cause) ->
                                writeJson(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                        AjaxResult.error(HttpServletResponse.SC_FORBIDDEN, "无权访问"))));

        return http.build();
    }

    private static void writeJson(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            AjaxResult<?> body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
