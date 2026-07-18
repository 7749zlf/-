package com.shortvideo.backend.admin.repository;

import java.util.List;

public record AdminUserRecord(
        Long id,
        String username,
        String passwordSalt,
        String passwordHash,
        String displayName,
        String roleKey,
        List<String> permissions,
        String status
) {
}
