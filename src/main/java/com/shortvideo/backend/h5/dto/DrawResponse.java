package com.shortvideo.backend.h5.dto;

public record DrawResponse(
        StorylineResponse line,
        UnlockOrderResponse order,
        String message
) {
}
