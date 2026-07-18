package com.shortvideo.backend.admin.dto;

public record AdminMediaAssetRequest(
        String title,
        String type,
        String drama,
        Long dramaId,
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
