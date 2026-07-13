package com.shortvideo.backend.admin;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/admin", "/admin"})
public class AdminSnapshotController {

    private final AdminSnapshotService snapshotService;
    private final AdminAuthService authService;

    public AdminSnapshotController(AdminSnapshotService snapshotService, AdminAuthService authService) {
        this.snapshotService = snapshotService;
        this.authService = authService;
    }

    @GetMapping("/snapshot")
    public JsonNode loadSnapshot(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        authService.requireAdmin(authorization, legacyToken);
        return snapshotService.loadSnapshot();
    }

    @PutMapping("/snapshot")
    public JsonNode saveSnapshot(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) JsonNode snapshot
    ) {
        authService.requireAdmin(authorization, legacyToken);
        return snapshotService.saveSnapshot(snapshot);
    }
}
