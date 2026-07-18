package com.shortvideo.backend.h5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.common.DataProtectionService;
import com.shortvideo.backend.common.PasswordHashService;
import com.shortvideo.backend.h5.dto.OauthLoginRequest;
import com.shortvideo.backend.h5.dto.SmsCodeRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class H5UserServiceProductionModeTests {

    private final H5UserService service = new H5UserService(
            null,
            new ObjectMapper(),
            new DataProtectionService("test-data-key-at-least-32-characters"),
            new PasswordHashService(),
            null,
            "https://api.example.com",
            "uploads/avatars",
            false,
            false,
            true
    );

    @Test
    void rejectsFakeSmsInProductionMode() {
        assertStatus(
                () -> service.sendCode(new SmsCodeRequest("login", "13800138000")),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Test
    void rejectsUnverifiedOauthInProductionMode() {
        assertStatus(
                () -> service.oauthLogin(new OauthLoginRequest("wechat", "code", "device")),
                HttpStatus.NOT_IMPLEMENTED
        );
    }

    @Test
    void rejectsDemoUploadSigningInProductionMode() {
        assertStatus(
                () -> service.avatarUploadSignature(new UploadSignatureRequest("avatar.png", "image/png")),
                HttpStatus.NOT_IMPLEMENTED
        );
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies((ex) -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(status));
    }
}
