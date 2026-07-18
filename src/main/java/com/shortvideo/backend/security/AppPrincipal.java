package com.shortvideo.backend.security;

import java.util.List;

public record AppPrincipal(
        Long id,
        String username,
        AppPrincipalType type,
        String roleKey,
        List<String> permissions
) {
}
