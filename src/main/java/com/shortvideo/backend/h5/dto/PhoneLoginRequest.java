package com.shortvideo.backend.h5.dto;

public record PhoneLoginRequest(
        String phone,
        String code,
        String deviceId
) {
}
