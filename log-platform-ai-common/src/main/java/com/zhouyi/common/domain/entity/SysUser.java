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

    private final Long userId;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean admin;
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
