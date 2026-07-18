package com.shortvideo.backend.h5.dto;

public record PaymentSettlementResponse(
        PaymentResponse payment,
        StorylineResponse line,
        UnlockOrderResponse order,
        String message
) {
}
