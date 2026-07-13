package com.shortvideo.backend.h5.dto;

public record BindPhoneRequest(
        String deviceId,
        String phone,
        String code
) {
}
