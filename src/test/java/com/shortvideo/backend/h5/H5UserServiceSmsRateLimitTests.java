package com.shortvideo.backend.h5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.common.DataProtectionService;
import com.shortvideo.backend.common.PasswordHashService;
import com.shortvideo.backend.h5.dto.SmsCodeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class H5UserServiceSmsRateLimitTests {

    @Test
    void rejectsRepeatedSmsWithinInterval() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        H5UserService service = new H5UserService(
                jdbc,
                new ObjectMapper(),
                new DataProtectionService("test-data-key-at-least-32-characters"),
                new PasswordHashService(),
                null,
                null,
                "http://localhost:8081",
                "uploads/avatars",
                true,
                true,
                false
        );

        assertThat(service.sendCode(new SmsCodeRequest("login", "13800138000")).debugCode()).isNotBlank();
        assertThatThrownBy(() -> service.sendCode(new SmsCodeRequest("login", "13800138000")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies((ex) -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
