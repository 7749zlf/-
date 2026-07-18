package com.shortvideo.backend.h5.dto;

public record H5RefundRequest(
        String deviceId,
        String orderId,
        String reason
) {
}
