package com.shortvideo.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record H5DramaPayload(
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String id,
        String title,
        String tag,
        Integer episodes,
        String heat,
        String cover,
        String status,
        Long playCount
) {
}
