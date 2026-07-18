package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDramaResponse(
        String id,
        Long rawId,
        String title,
        String category,
        String status,
        Integer episodes,
        Integer uploaded,
        String price,
        String unlockRate,
        BigDecimal revenue,
        Long playCount,
        String owner,
        String reviewNote,
        String updated,
        Integer progress,
        String cover,
        List<String> tags,
        List<EpisodeItem> episodesList,
        List<AdminContentReviewLogResponse> reviewLogs
) {
    public record EpisodeItem(
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
}
