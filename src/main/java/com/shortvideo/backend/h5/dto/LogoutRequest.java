package com.shortvideo.backend.h5.dto;

public record LogoutRequest(
        String deviceId,
        String refreshToken
) {
}
