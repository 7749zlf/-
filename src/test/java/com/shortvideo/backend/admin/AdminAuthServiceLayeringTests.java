package com.shortvideo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.admin.repository.AdminAuthRepository;
import com.shortvideo.backend.admin.repository.AdminUserRecord;
import com.shortvideo.backend.common.PasswordHashService;
import org.junit.jupiter.api.Test;

class AdminAuthServiceLayeringTests {

    @Test
    void currentAdminProfileUsesRepositoryLayer() {
        AdminAuthRepository authRepository = mock(AdminAuthRepository.class);
        AdminUserRecord admin = new AdminUserRecord(
                7L,
                "admin",
                "salt",
                "hash",
                "Administrator",
                "operator",
                List.of("fallback"),
                "ENABLED"
        );
        when(authRepository.findActiveAdminByToken("adm_token")).thenReturn(Optional.of(admin));
        when(authRepository.findRolePermissions("operator")).thenReturn(List.of("dashboard"));
        AdminAuthService service = new AdminAuthService(
                new PasswordHashService(),
                authRepository,
                "admin",
                "Admin@123456",
                "Administrator",
                "",
                5,
                10
        );

        AdminProfileResponse profile = service.current("Bearer adm_token", null);

        assertThat(profile.id()).isEqualTo(7L);
        assertThat(profile.permissions()).containsExactly("dashboard");
        verify(authRepository).findActiveAdminByToken("adm_token");
        verify(authRepository).findRolePermissions("operator");
    }
}
