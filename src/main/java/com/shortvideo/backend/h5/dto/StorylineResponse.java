package com.shortvideo.backend.h5.dto;

public record StorylineResponse(
        String id,
        Long dramaId,
        String name,
        String rarity,
        String desc,
        String cover
) {
}
