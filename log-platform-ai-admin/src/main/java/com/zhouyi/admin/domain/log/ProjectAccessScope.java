package com.zhouyi.admin.domain.log;

import java.util.Set;

/** 当前登录用户可访问的日志项目范围。 */
public record ProjectAccessScope(
        /** 是否为平台管理员，管理员可访问全部启用项目。 */
        boolean admin,
        /** 普通用户被授予的项目 ID 集合。 */
        Set<String> projectIds) {

    public ProjectAccessScope {
        projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
    }

    public static ProjectAccessScope administrator() {
        return new ProjectAccessScope(true, Set.of());
    }

    public static ProjectAccessScope restricted(Set<String> projectIds) {
        return new ProjectAccessScope(false, projectIds);
    }
}
