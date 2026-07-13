package com.shortvideo.backend.h5.dto;

public record SmsCodeResponse(
        Boolean success,
        Boolean sent,
        Integer cooldownSeconds,
        Integer cooldown,
        String requestId
) {
}
