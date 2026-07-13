package com.shortvideo.backend.h5.dto;

public record H5PreferencesResponse(
        Boolean autoPlayNext,
        Boolean unlockReminder,
        Boolean muted
) {
}
