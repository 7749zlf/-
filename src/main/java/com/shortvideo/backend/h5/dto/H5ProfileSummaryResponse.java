package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;

public record H5ProfileSummaryResponse(
        BigDecimal balance,
        BigDecimal paidAmount,
        Integer followedCount,
        Integer likedCount,
        Integer watchHistoryCount,
        Integer unlockedCount,
        Integer orderCount,
        Integer rechargeCount
) {
}
