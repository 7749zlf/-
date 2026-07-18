package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;

public record PaymentRequest(
        String deviceId,
        Long dramaId,
        String storylineId,
        String episodeId,
        BigDecimal amount,
        String methodKey,
        String methodName
) {
}
