package com.shortvideo.backend.admin.dto;

public record AdminAuthResponse(
        String token,
        Integer expiresIn,
        AdminProfileResponse admin
) {
}
