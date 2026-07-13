package com.shortvideo.backend.h5.dto;

public record EpisodeAccessResponse(
        Boolean canPlay,
        Boolean chargeable,
        String nextEpisodeId,
        Integer nextEpisodeNumber,
        String storylineId,
        PayNodeResponse payNode
) {
}
