package com.zhouyi.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * @author zjz
 * @version 1.0
 * @description: 系统用户
 * @date 2026/9/12 17:32
 */
public class SysUser implements Serializable {

    /** 用户主键。 */
    private final Long userId;
    /** 登录用户名。 */
    private final String username;
    /** BCrypt 密码哈希，序列化时必须隐藏。 */
    private final String password;
    /** 用户展示名称。 */
    private final String displayName;
    /** 是否拥有平台管理员权限。 */
    private final boolean admin;
    /** 是否允许登录和访问平台。 */
    private final boolean enabled;

    public SysUser(Long userId, String username, String password, String displayName, boolean admin, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.admin = admin;
        this.enabled = enabled;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAdmin() {
        return admin;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
