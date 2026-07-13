package com.shortvideo.backend.h5.dto;

public record PaymentCallbackRequest(
        String paymentId,
        String providerTradeNo,
        String status
) {
}
