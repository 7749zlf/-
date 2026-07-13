package com.shortvideo.backend.h5.dto;

public record UploadSignatureResponse(
        String uploadUrl,
        String publicUrl,
        Integer expiresIn
) {
}
