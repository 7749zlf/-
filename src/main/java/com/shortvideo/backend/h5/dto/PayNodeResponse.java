package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;

public record PayNodeResponse(
        String type,
        String title,
        String subtitle,
        String amount,
        BigDecimal amountValue,
        String currency,
        String episodeId,
        Long dramaId
) {
}
