package com.shortvideo.backend.admin.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record H5SnapshotPayload(
        List<H5DramaPayload> dramas,
        List<H5EpisodePayload> episodes,
        List<H5StorylinePayload> storylines
) {
    public H5SnapshotPayload {
        dramas = dramas == null ? List.of() : List.copyOf(dramas);
        episodes = episodes == null ? List.of() : List.copyOf(episodes);
        storylines = storylines == null ? List.of() : List.copyOf(storylines);
    }
}
