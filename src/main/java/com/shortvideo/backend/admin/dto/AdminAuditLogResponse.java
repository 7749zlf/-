package com.shortvideo.backend.admin.dto;

public record AdminAuditLogResponse(
        Long id,
        String action,
        String module,
        String detail,
        String time,
        String actor
) {
}
