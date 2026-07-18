package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminFinanceResponse(
        List<FinanceRow> financeRows,
        List<RecentOrder> orders
) {
    public record FinanceRow(
            String drama,
            String channel,
            BigDecimal revenue,
            BigDecimal cost,
            Integer orders,
            String roi
    ) {
    }

    public record RecentOrder(
            String id,
            String user,
            String item,
            String amount,
            String status,
            String time
    ) {
    }
}
