package com.shortvideo.backend.admin.dto;

import java.util.List;

public record AdminProfileResponse(
        Long id,
        String username,
        String name,
        String role,
        List<String> permissions
) {
}
