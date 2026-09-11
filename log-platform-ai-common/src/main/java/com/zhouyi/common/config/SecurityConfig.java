package com.zhouyi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * @author zjz
 * @version 1.0
 * @description:
 * @date 2026/9/11 11:17
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 密码编码器：使用 BCrypt
     * 强度因子 12 表示计算时间约为默认值(10)的4倍，约300-500ms
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 简单内存用户存储
     * 如需数据库认证，替换为 JdbcUserDetailsManager 或自定义 UserDetailsService
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // 管理员账号
        var admin = User.withUsername("admin")
                .password(passwordEncoder.encode("admin@2024"))
                .roles("ADMIN")
                .build();

        // 普通查看用户
        var viewer = User.withUsername("viewer")
                .password(passwordEncoder.encode("viewer@2024"))
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(admin, viewer);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（日志平台无表单提交，纯查询场景）
                .csrf(AbstractHttpConfigurer::disable)

                // 关闭 Session，使用有状态的 Session 认证
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                )

                .authorizeHttpRequests(auth -> auth
                        // 静态资源放行
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // 登录页面及登录接口放行
                        .requestMatchers("/login", "/doLogin").permitAll()
                        // 日志查询接口需要认证
                        .requestMatchers("/api/logs/**", "/api/trace/**").authenticated()
                        // 管理接口需要 ADMIN 角色
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // /monitor 路径下的所有请求需要认证
                        .requestMatchers("/monitor/**").authenticated()
                        // 其余请求统一需要认证
                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")                // 自定义登录页面路径
                        .loginProcessingUrl("/doLogin")      // 表单提交处理路径
                        .defaultSuccessUrl("/monitor/", true) // 登录成功后跳转
                        .failureUrl("/login?error=true")     // 登录失败跳转
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )

                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/403")
                );

        return http.build();
    }
}