package com.shortvideo.backend.h5.dto;

import java.time.LocalDateTime;

public record WatchHistoryResponse(
        Long id,
        Long dramaId,
        String dramaTitle,
        String storylineId,
        String storylineName,
        String episodeId,
        Integer episodeNumber,
        String episodeTitle,
        String cover,
        Integer progressSeconds,
        Integer durationSeconds,
        LocalDateTime updatedAt
) {
}
