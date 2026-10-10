package com.zhouyi.admin.service;

import com.zhouyi.admin.repository.UserProjectRepository;
import com.zhouyi.common.domain.entity.SysUser;
import com.zhouyi.common.domain.model.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectAuthorizationServiceTest {

    private final UserProjectRepository repository = mock(UserProjectRepository.class);
    private final ProjectAuthorizationService service = new ProjectAuthorizationService(repository);

    @Test
    void administratorCanAccessAllProjectsWithoutMembershipLookup() {
        var scope = service.resolve(authentication(1L, true));

        assertThat(scope.admin()).isTrue();
        assertThat(scope.projectIds()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void viewerReceivesOnlyAssignedProjects() {
        when(repository.findEnabledProjectIdsByUserId(2L)).thenReturn(Set.of("billing"));

        var scope = service.resolve(authentication(2L, false));

        assertThat(scope.admin()).isFalse();
        assertThat(scope.projectIds()).containsExactly("billing");
    }

    @Test
    void viewerWithoutProjectsIsRejected() {
        when(repository.findEnabledProjectIdsByUserId(2L)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.resolve(authentication(2L, false)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("当前用户未分配任何项目权限");
    }

    private static UsernamePasswordAuthenticationToken authentication(Long id, boolean admin) {
        LoginUser user = new LoginUser(new SysUser(id, "user" + id, "hash", "User", admin, true));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
