package com.shortvideo.backend.admin.dto;

public record AdminContentReviewLogResponse(
        Long id,
        String targetType,
        String targetId,
        Long dramaId,
        String title,
        String status,
        String note,
        String actor,
        String createdAt
) {
}
