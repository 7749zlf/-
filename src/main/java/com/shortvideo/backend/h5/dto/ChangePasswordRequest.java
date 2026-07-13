package com.shortvideo.backend.h5.dto;

public record ChangePasswordRequest(
        String deviceId,
        String oldPassword,
        String newPassword
) {
}
