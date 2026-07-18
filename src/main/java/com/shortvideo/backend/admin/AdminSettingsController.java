package com.shortvideo.backend.admin;

import java.util.Map;

import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final AdminAuthService authService;
    private final AdminSettingsService settingsService;

    public AdminSettingsController(AdminAuthService authService, AdminSettingsService settingsService) {
        this.authService = authService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public Map<String, Object> settings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        authService.requirePermission(authorization, legacyToken, "settings");
        return settingsService.getSettings();
    }

    @PutMapping
    public Map<String, Object> updateSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        AdminProfileResponse actor = authService.requirePermission(authorization, legacyToken, "settings");
        return settingsService.updateSettings(request, actor);
    }
}
