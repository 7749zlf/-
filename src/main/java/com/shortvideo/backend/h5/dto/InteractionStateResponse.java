package com.shortvideo.backend.h5.dto;

import java.util.List;

public record InteractionStateResponse(
        boolean ok,
        Long dramaId,
        String episodeId,
        boolean followed,
        boolean liked,
        Integer followedCount,
        Integer likedCount,
        List<FollowedDramaResponse> followedDramas,
        List<LikedEpisodeResponse> likedEpisodes
) {
}
