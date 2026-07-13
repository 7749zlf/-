package com.shortvideo.backend.h5.dto;

import java.util.List;

public record H5SnapshotResponse(
        List<DramaResponse> dramas,
        List<EpisodeResponse> episodes,
        List<StorylineResponse> storylines
) {
}
