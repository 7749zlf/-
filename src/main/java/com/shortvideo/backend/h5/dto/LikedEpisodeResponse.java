package com.shortvideo.backend.h5.dto;

import java.time.LocalDateTime;

public record LikedEpisodeResponse(
        String id,
        Long dramaId,
        String dramaTitle,
        String storylineId,
        String storylineName,
        Integer episodeNumber,
        String episodeTitle,
        String cover,
        LocalDateTime updatedAt
) {
}
