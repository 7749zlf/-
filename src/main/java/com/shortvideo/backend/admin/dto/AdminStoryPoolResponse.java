package com.shortvideo.backend.admin.dto;

import java.util.List;

public record AdminStoryPoolResponse(
        String id,
        String name,
        String drama,
        Long rawDramaId,
        String status,
        Integer entries,
        String price,
        Long unlocked,
        String paidRate,
        List<WeightItem> weights
) {
    public record WeightItem(
            String label,
            Double value
    ) {
    }
}
