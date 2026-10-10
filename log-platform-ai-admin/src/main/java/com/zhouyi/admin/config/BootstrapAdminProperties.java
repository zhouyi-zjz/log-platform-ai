package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "log-platform.bootstrap-admin")
public class BootstrapAdminProperties {

    /** 启动时创建或同步的管理员用户名。 */
    private String username = "";
    /** 启动时注入的管理员明文密码，仅存在于 Secret 和启动内存。 */
    private String password = "";
    /** 管理员展示名称。 */
    private String displayName = "系统管理员";

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
