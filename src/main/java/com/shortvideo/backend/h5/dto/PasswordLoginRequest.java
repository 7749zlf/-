package com.shortvideo.backend.h5.dto;

public record PasswordLoginRequest(
        String account,
        String password,
        String deviceId
) {
}
