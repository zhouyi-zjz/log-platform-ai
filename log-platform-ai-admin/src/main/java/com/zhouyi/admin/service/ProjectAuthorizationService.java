package com.zhouyi.admin.service;

import com.zhouyi.admin.domain.log.ProjectAccessScope;
import com.zhouyi.admin.repository.UserProjectRepository;
import com.zhouyi.common.domain.model.LoginUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ProjectAuthorizationService {

    private final UserProjectRepository userProjectRepository;

    public ProjectAuthorizationService(UserProjectRepository userProjectRepository) {
        this.userProjectRepository = userProjectRepository;
    }

    /** 从服务端登录态和数据库关系解析项目范围，无法解析时默认拒绝。 */
    public ProjectAccessScope resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new AccessDeniedException("无法识别当前用户的项目权限");
        }
        if (user.isAdmin()) {
            return ProjectAccessScope.administrator();
        }
        Set<String> projectIds = userProjectRepository.findEnabledProjectIdsByUserId(user.getUserId());
        if (projectIds.isEmpty()) {
            throw new AccessDeniedException("当前用户未分配任何项目权限");
        }
        return ProjectAccessScope.restricted(projectIds);
    }
}
