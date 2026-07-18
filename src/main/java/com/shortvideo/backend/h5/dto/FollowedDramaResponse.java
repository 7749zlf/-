package com.shortvideo.backend.h5.dto;

import java.time.LocalDateTime;

public record FollowedDramaResponse(
        Long id,
        String title,
        String tag,
        Integer episodeCount,
        String cover,
        LocalDateTime updatedAt
) {
}
