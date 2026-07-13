package com.shortvideo.backend.h5.dto;

public record EpisodeResponse(
        String id,
        Long dramaId,
        Integer number,
        String title,
        String storylineId,
        String cover,
        String videoUrl
) {
}
