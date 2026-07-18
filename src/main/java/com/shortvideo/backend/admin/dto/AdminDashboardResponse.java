package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        List<Kpi> kpis,
        List<TrendPoint> trend,
        List<TopDrama> topDramas,
        List<TaskItem> tasks
) {
    public record Kpi(
            String label,
            String value,
            String change,
            String note,
            String tone
    ) {
    }

    public record TrendPoint(
            String day,
            Integer value
    ) {
    }

    public record TopDrama(
            String id,
            Long rawId,
            String title,
            String category,
            String unlockRate,
            BigDecimal revenue,
            String cover
    ) {
    }

    public record TaskItem(
            String text,
            Boolean done,
            String panel
    ) {
    }
}
