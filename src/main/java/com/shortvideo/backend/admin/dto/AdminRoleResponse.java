package com.shortvideo.backend.admin.dto;

import java.util.List;

public record AdminRoleResponse(
        String id,
        String name,
        Integer members,
        String status,
        List<String> permissions
) {
}
