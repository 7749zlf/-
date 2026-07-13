package com.shortvideo.backend.h5.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PlayEventRequest(
        String deviceId,
        String eventType,
        Long dramaId,
        String episodeId,
        String storylineId,
        JsonNode payload
) {
}
