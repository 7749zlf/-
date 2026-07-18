package com.shortvideo.backend.h5.dto;

public record UnlockOrderResponse(
        String id,
        String title,
        String amount,
        String paymentMethod,
        String paymentMethodKey,
        String time,
        String status,
        String refundStatus,
        String refundReason
) {
}
