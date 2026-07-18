package com.shortvideo.backend.admin.dto;

public record AdminDramaRequest(
        String title,
        String category,
        String status,
        Integer episodes,
        String price,
        String cover,
        String owner,
        String reviewNote
) {
}
