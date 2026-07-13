package com.shortvideo.backend.admin.dto;

public record AdminUserRequest(
        String id,
        String deviceId,
        String name,
        String level,
        String paid,
        String status,
        String email,
        String note
) {
}
