package com.shortvideo.backend.h5.dto;

public record H5AuthResponse(
        String token,
        String refreshToken,
        Integer expiresIn,
        H5ProfileResponse user
) {
}
