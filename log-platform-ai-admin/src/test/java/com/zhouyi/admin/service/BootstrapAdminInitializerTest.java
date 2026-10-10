package com.zhouyi.admin.service;

import com.zhouyi.admin.config.BootstrapAdminProperties;
import com.zhouyi.admin.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BootstrapAdminInitializerTest {

    private final SysUserRepository repository = mock(SysUserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void createsOrSynchronizesConfiguredAdministrator() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties();
        properties.setUsername("admin");
        properties.setPassword("a-strong-password");
        properties.setDisplayName("平台管理员");
        when(passwordEncoder.encode("a-strong-password")).thenReturn("bcrypt-hash");
        when(repository.upsertAdmin("admin", "bcrypt-hash", "平台管理员")).thenReturn(true);

        new BootstrapAdminInitializer(properties, repository, passwordEncoder)
                .run(new DefaultApplicationArguments());

        verify(repository).upsertAdmin("admin", "bcrypt-hash", "平台管理员");
    }

    @Test
    void skipsBootstrapWhenCredentialsAreNotConfigured() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties();

        new BootstrapAdminInitializer(properties, repository, passwordEncoder)
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void rejectsPartialOrWeakCredentials() {
        BootstrapAdminProperties missingPassword = new BootstrapAdminProperties();
        missingPassword.setUsername("admin");
        assertThatThrownBy(() -> new BootstrapAdminInitializer(
                missingPassword, repository, passwordEncoder).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须同时配置");

        BootstrapAdminProperties weakPassword = new BootstrapAdminProperties();
        weakPassword.setUsername("admin");
        weakPassword.setPassword("too-short");
        assertThatThrownBy(() -> new BootstrapAdminInitializer(
                weakPassword, repository, passwordEncoder).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 12 个字符");
    }
}
