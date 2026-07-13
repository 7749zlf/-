package com.shortvideo.backend.h5.dto;

public record UpdateProfileRequest(
        String deviceId,
        String nickname,
        String avatar,
        String gender,
        String birthday,
        String bio
) {
}
