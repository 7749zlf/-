package com.shortvideo.backend.h5.dto;

public record GuestLoginRequest(
        String deviceId,
        String source,
        String channel
) {
}
