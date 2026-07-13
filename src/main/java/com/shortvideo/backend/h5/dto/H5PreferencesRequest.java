package com.shortvideo.backend.h5.dto;

public record H5PreferencesRequest(
        String deviceId,
        Boolean autoPlayNext,
        Boolean unlockReminder,
        Boolean muted
) {
}
