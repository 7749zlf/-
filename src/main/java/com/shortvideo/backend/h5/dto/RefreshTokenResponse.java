package com.shortvideo.backend.h5.dto;

public record RefreshTokenResponse(
        String token,
        String refreshToken,
        Integer expiresIn
) {
}
