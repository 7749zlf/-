package com.shortvideo.backend.h5.dto;

public record OauthLoginRequest(
        String provider,
        String code,
        String deviceId
) {
}
