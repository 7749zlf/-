package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;

public record AdminOrderRequest(
        String user,
        String drama,
        String item,
        BigDecimal amount,
        String method,
        String status,
        String risk,
        String entitlement,
        String note
) {
}
