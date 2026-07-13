package com.shortvideo.backend.h5;

import com.shortvideo.backend.h5.dto.H5SnapshotResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class H5SnapshotController {

    private final H5Service h5Service;

    public H5SnapshotController(H5Service h5Service) {
        this.h5Service = h5Service;
    }

    @GetMapping({"/api/h5/snapshot", "/h5/snapshot"})
    public H5SnapshotResponse snapshot(@RequestParam(required = false) String deviceId) {
        return h5Service.snapshot(deviceId);
    }
}
