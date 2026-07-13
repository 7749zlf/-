package com.shortvideo.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record H5StorylinePayload(
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String id,
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String dramaId,
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String poolId,
        String name,
        String rarity,
        String desc,
        String cover,
        String status,
        String price
) {
}
