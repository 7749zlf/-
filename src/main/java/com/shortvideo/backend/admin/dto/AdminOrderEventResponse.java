package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;

public record AdminOrderEventResponse(
        Long id,
        String orderId,
        String eventType,
        String fromStatus,
        String toStatus,
        BigDecimal amount,
        String reason,
        String actorUsername,
        String createdAt
) {
}
