package com.shortvideo.backend.admin.dto;

public record AdminLoginRequest(
        String username,
        String password
) {
}
