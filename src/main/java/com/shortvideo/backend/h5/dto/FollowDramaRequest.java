package com.shortvideo.backend.h5.dto;

public record FollowDramaRequest(
        String deviceId,
        Long dramaId
) {
}
