package com.shortvideo.backend.h5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.common.DataProtectionService;
import com.shortvideo.backend.common.PasswordHashService;
import com.shortvideo.backend.h5.repository.H5AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class H5UserServicePaymentStatusTests {

    private final H5UserService service = serviceWithAuthTokenRepository(null);

    @Test
    void acceptsKnownPaymentStatusesOnly() {
        assertThat(service.normalizePaymentStatus("SUCCESS")).isEqualTo("PAID");
        assertThat(service.normalizePaymentStatus("PAID")).isEqualTo("PAID");
        assertThat(service.normalizePaymentStatus("FAILED")).isEqualTo("FAILED");
        assertThat(service.normalizePaymentStatus("CANCELLED")).isEqualTo("CANCELLED");
        assertThat(service.normalizePaymentStatus("PENDING")).isEqualTo("PENDING");
    }

    @Test
    void rejectsBlankOrUnknownPaymentStatuses() {
        assertBadStatus("");
        assertBadStatus(null);
        assertBadStatus("whatever");
    }

    @Test
    void ignoresNonH5BearerTokenWhenAuthenticatingH5User() {
        assertThat(service.authenticatedUserId("Bearer adm_token")).isEmpty();
    }

    @Test
    void authenticatesH5BearerTokenThroughRepositoryLayer() {
        H5AuthTokenRepository authTokenRepository = mock(H5AuthTokenRepository.class);
        when(authTokenRepository.findActiveUserIdByAccessToken("h5_token")).thenReturn(Optional.of(42L));
        H5UserService layeredService = serviceWithAuthTokenRepository(authTokenRepository);

        assertThat(layeredService.authenticatedUserId("Bearer h5_token")).contains(42L);
        verify(authTokenRepository).findActiveUserIdByAccessToken("h5_token");
    }

    private H5UserService serviceWithAuthTokenRepository(H5AuthTokenRepository authTokenRepository) {
        return new H5UserService(
                null,
                new ObjectMapper(),
            new DataProtectionService("test-data-key-at-least-32-characters"),
            new PasswordHashService(),
            authTokenRepository,
            null,
            "http://localhost:8081",
                "uploads/avatars",
                true,
                true,
                false
        );
    }

    private void assertBadStatus(String status) {
        assertThatThrownBy(() -> service.normalizePaymentStatus(status))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies((ex) -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
