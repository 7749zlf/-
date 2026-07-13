package com.shortvideo.backend.h5.dto;

public record DramaResponse(
        Long id,
        String title,
        String tag,
        Integer episodes,
        String heat,
        String cover
) {
}
