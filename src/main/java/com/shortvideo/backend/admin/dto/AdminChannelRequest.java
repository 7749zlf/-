package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;

public record AdminChannelRequest(
        String name,
        String source,
        String owner,
        String status,
        BigDecimal budget,
        BigDecimal spent,
        BigDecimal revenue,
        Integer installs,
        Integer payUsers
) {
}
