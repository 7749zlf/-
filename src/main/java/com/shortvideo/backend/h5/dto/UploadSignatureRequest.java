package com.shortvideo.backend.h5.dto;

public record UploadSignatureRequest(
        String fileName,
        String contentType
) {
}
