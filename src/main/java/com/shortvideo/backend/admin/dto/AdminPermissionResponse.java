package com.shortvideo.backend.admin.dto;

public record AdminPermissionResponse(
        String key,
        String label,
        String description
) {
}
