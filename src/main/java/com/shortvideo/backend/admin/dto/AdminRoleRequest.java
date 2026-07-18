package com.shortvideo.backend.admin.dto;

import java.util.List;

public record AdminRoleRequest(
        String name,
        String status,
        List<String> permissions
) {
}
