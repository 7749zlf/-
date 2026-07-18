package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;

public record AdminRefundRequestResponse(
        String id,
        String orderId,
        String user,
        BigDecimal amount,
        String reason,
        String status,
        String reviewReason,
        String reviewerUsername,
        String createdAt,
        String reviewedAt
) {
}
