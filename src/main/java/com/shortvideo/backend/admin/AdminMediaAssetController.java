package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminMediaAssetRequest;
import com.shortvideo.backend.admin.dto.AdminMediaAssetResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/media-assets")
public class AdminMediaAssetController {

    private final AdminAuthService authService;
    private final AdminMediaAssetManagementService mediaAssetService;

    public AdminMediaAssetController(AdminAuthService authService, AdminMediaAssetManagementService mediaAssetService) {
        this.authService = authService;
        this.mediaAssetService = mediaAssetService;
    }

    @GetMapping
    public List<AdminMediaAssetResponse> listAssets(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String keyword
    ) {
        authService.requirePermission(authorization, legacyToken, "media");
        return mediaAssetService.listAssets(keyword);
    }

    @GetMapping("/{id}")
    public AdminMediaAssetResponse asset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id
    ) {
        authService.requirePermission(authorization, legacyToken, "media");
        return mediaAssetService.getAsset(id);
    }

    @PostMapping
    public AdminMediaAssetResponse createAsset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) AdminMediaAssetRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "media");
        return mediaAssetService.createAsset(request);
    }

    @PutMapping("/{id}")
    public AdminMediaAssetResponse updateAsset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminMediaAssetRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "media");
        return mediaAssetService.updateAsset(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminMediaAssetResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminMediaAssetRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "media");
        return mediaAssetService.updateStatus(id, request == null ? "待审核" : request.status());
    }
}
