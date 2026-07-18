package com.shortvideo.backend.h5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.common.DataProtectionService;
import com.shortvideo.backend.common.PasswordHashService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class H5UserServicePaymentStatusTests {

    private final H5UserService service = new H5UserService(
            null,
            new ObjectMapper(),
            new DataProtectionService("test-data-key-at-least-32-characters"),
            new PasswordHashService(),
            "http://localhost:8081",
            "uploads/avatars",
            true,
            true,
            false
    );

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

    private void assertBadStatus(String status) {
        assertThatThrownBy(() -> service.normalizePaymentStatus(status))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies((ex) -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
