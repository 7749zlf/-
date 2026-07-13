package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RechargeResponse(
        String id,
        BigDecimal amount,
        String amountText,
        String methodKey,
        String methodName,
        String status,
        LocalDateTime createdAt
) {
}
