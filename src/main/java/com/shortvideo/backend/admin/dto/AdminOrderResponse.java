package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;

public record AdminOrderResponse(
        String id,
        String user,
        String drama,
        String item,
        BigDecimal amount,
        String method,
        String status,
        String risk,
        String time,
        String entitlement,
        String note,
        Long rawUserId,
        Long rawDramaId
) {
}
