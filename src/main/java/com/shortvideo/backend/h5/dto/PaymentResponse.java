package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        String paymentId,
        String status,
        BigDecimal amount,
        String amountText,
        String methodKey,
        String methodName,
        String checkoutUrl
) {
}
