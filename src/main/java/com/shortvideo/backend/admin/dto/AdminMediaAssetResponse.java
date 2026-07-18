package com.shortvideo.backend.admin.dto;

public record AdminMediaAssetResponse(
        String id,
        String title,
        String type,
        String drama,
        Long rawDramaId,
        String duration,
        String size,
        String status,
        String usage,
        String owner,
        String updated,
        String reviewNote,
        String cover,
        String videoUrl
) {
}
