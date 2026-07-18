package com.shortvideo.backend.h5.dto;

public record DrawRequest(
        String deviceId,
        Long dramaId,
        String episodeId,
        String amount,
        String methodKey,
        String methodName
) {
}
