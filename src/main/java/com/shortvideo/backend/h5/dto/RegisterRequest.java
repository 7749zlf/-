package com.shortvideo.backend.h5.dto;

public record RegisterRequest(
        String phone,
        String code,
        String password,
        String nickname,
        String deviceId
) {
}
