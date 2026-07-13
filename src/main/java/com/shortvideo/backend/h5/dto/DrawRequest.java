package com.shortvideo.backend.h5.dto;

public record DrawRequest(
        String deviceId,
        Long dramaId,
        String amount,
        String methodKey,
        String methodName
) {
}
