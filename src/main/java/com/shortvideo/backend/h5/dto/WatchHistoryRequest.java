package com.shortvideo.backend.h5.dto;

public record WatchHistoryRequest(
        String deviceId,
        Long dramaId,
        String storylineId,
        String episodeId,
        Integer episodeNumber,
        Integer progressSeconds,
        Integer durationSeconds
) {
}
