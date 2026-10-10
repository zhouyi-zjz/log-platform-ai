package com.zhouyi.admin.service;

import com.zhouyi.admin.config.BootstrapAdminProperties;
import com.zhouyi.admin.repository.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties,
                                     SysUserRepository userRepository,
                                     PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = trim(properties.getUsername());
        String password = properties.getPassword() == null ? "" : properties.getPassword();
        if (username.isEmpty() && password.isEmpty()) {
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME 和 BOOTSTRAP_ADMIN_PASSWORD 必须同时配置");
        }
        if (!username.matches("[a-zA-Z0-9_.@-]{3,64}")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME 格式无效");
        }
        if (password.length() < 12) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD 至少需要 12 个字符");
        }
        String displayName = trim(properties.getDisplayName());
        if (displayName.isEmpty()) {
            displayName = "系统管理员";
        }
        if (userRepository.upsertAdmin(username, passwordEncoder.encode(password), displayName)) {
            log.info("已创建启动管理员账号 {}，密码未写入日志", username);
        } else {
            log.info("已从 Secret 同步启动管理员账号 {}，密码未写入日志", username);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
