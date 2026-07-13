package com.shortvideo.backend.admin.dto;

public record AdminUserResponse(
        String id,
        Long numericId,
        String deviceId,
        String phone,
        String name,
        String level,
        String paid,
        String watch,
        String status,
        String lastActive,
        String email,
        String note
) {
}
