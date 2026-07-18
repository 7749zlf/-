package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminReconciliationResponse(
        Summary summary,
        List<Row> rows
) {
    public record Summary(
            Integer total,
            Integer matched,
            Integer pending,
            Integer refunded,
            Integer mismatch,
            BigDecimal paidAmount,
            BigDecimal refundedAmount
    ) {
    }

    public record Row(
            String paymentId,
            String orderId,
            String user,
            BigDecimal paymentAmount,
            BigDecimal orderAmount,
            String paymentStatus,
            String orderStatus,
            String reconciliationStatus,
            String mismatchReason,
            String method,
            String providerTradeNo,
            Integer eventCount,
            String latestEventType,
            String createdAt,
            String paidAt
    ) {
    }
}
