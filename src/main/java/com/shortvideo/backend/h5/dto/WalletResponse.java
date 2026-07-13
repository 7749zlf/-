package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;

public record WalletResponse(
        String deviceId,
        BigDecimal balance,
        BigDecimal paidAmount,
        Integer orderCount,
        Integer rechargeCount
) {
}
