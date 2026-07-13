package com.shortvideo.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record H5EpisodePayload(
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String id,
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String dramaId,
        Integer number,
        String title,
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String storylineId,
        String cover,
        String videoUrl,
        String status,
        Boolean isPayNode
) {
}
