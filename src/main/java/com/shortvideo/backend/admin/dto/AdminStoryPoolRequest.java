package com.shortvideo.backend.admin.dto;

import java.util.List;

public record AdminStoryPoolRequest(
        String name,
        String drama,
        Long dramaId,
        String status,
        Integer entries,
        String price,
        List<WeightItem> weights
) {
    public record WeightItem(
            String label,
            Double value
    ) {
    }
}
