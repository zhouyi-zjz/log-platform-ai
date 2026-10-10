package com.zhouyi.admin.service;

import com.zhouyi.admin.repository.ProjectTokenRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 项目 Token 只保存摘要且能正确完成轮换和校验。 */
class ProjectTokenServiceTest {

    private final ProjectTokenRepository repository = mock(ProjectTokenRepository.class);
    private final ProjectTokenService service = new ProjectTokenService(repository);

    @Test
    void generatedTokenCanBeValidatedWithoutStoringPlaintext() {
        when(repository.updateTokenHash(anyString(), anyString())).thenReturn(true);
        String token = service.rotate("billing");

        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).updateTokenHash(org.mockito.ArgumentMatchers.eq("billing"), hash.capture());
        when(repository.findEnabledTokenHash("billing")).thenReturn(Optional.of(hash.getValue()));

        assertThat(token).hasSizeGreaterThan(32);
        assertThat(service.isValid("billing", token)).isTrue();
        assertThat(service.isValid("orders", token)).isFalse();
        assertThat(service.isValid("billing", token + "x")).isFalse();
    }
}
