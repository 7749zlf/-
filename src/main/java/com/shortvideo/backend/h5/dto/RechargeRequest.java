package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;

public record RechargeRequest(
        String deviceId,
        BigDecimal amount,
        String methodKey,
        String methodName
) {
}
