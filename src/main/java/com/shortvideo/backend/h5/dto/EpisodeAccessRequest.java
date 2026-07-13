package com.shortvideo.backend.h5.dto;

import java.util.List;

public record EpisodeAccessRequest(
        String deviceId,
        Long dramaId,
        String currentEpisodeId,
        Integer currentEpisodeNumber,
        String nextEpisodeId,
        Integer nextEpisodeNumber,
        String storylineId,
        List<String> unlockedLineIds
) {
}
