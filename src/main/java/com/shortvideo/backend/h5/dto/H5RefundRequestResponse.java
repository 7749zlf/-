package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;

public record H5RefundRequestResponse(
        String id,
        String orderId,
        BigDecimal amount,
        String reason,
        String status,
        String reviewReason,
        String createdAt,
        String reviewedAt
) {
}
