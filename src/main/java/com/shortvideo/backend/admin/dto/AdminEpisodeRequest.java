package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;

public record AdminEpisodeRequest(
        String id,
        Integer index,
        String title,
        String duration,
        String status,
        Boolean payNode,
        String cover,
        String videoUrl,
        String storylineId,
        BigDecimal unlockPrice,
        String price,
        String reviewNote
) {
}
