package com.shortvideo.backend.h5.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record H5ProfileResponse(
        Long id,
        String deviceId,
        String phone,
        String nickname,
        String avatar,
        String level,
        BigDecimal balance,
        String status,
        Boolean phoneBound,
        BigDecimal paidAmount,
        Integer watchCount,
        Integer unlockedCount,
        Integer orderCount,
        List<Long> followedDramaIds,
        List<String> likedEpisodeIds,
        H5PreferencesResponse preferences,
        LocalDateTime createdAt,
        LocalDateTime lastActiveAt
) {
}
