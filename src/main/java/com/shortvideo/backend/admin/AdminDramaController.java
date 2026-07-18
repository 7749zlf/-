package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminDramaRequest;
import com.shortvideo.backend.admin.dto.AdminDramaResponse;
import com.shortvideo.backend.admin.dto.AdminEpisodeRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/dramas")
public class AdminDramaController {

    private final AdminAuthService authService;
    private final AdminDramaManagementService dramaService;

    public AdminDramaController(AdminAuthService authService, AdminDramaManagementService dramaService) {
        this.authService = authService;
        this.dramaService = dramaService;
    }

    @GetMapping
    public List<AdminDramaResponse> listDramas(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String keyword
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.listDramas(keyword);
    }

    @GetMapping("/{id}")
    public AdminDramaResponse drama(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.getDrama(id);
    }

    @PostMapping
    public AdminDramaResponse createDrama(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) AdminDramaRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.createDrama(request);
    }

    @PutMapping("/{id}")
    public AdminDramaResponse updateDrama(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminDramaRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.updateDrama(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminDramaResponse updateDramaStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminDramaRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.updateDramaStatus(
                id,
                request == null ? null : request.status(),
                request == null ? null : request.reviewNote()
        );
    }

    @PostMapping("/{id}/episodes/batch-import")
    public AdminDramaResponse batchImportEpisodes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) List<AdminEpisodeRequest> request
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.batchImportEpisodes(id, request);
    }

    @PostMapping("/{id}/episodes")
    public AdminDramaResponse createEpisode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminEpisodeRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.createEpisode(id, request);
    }

    @PutMapping("/{id}/episodes/{episodeId}")
    public AdminDramaResponse updateEpisode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @PathVariable String episodeId,
            @RequestBody(required = false) AdminEpisodeRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.updateEpisode(id, episodeId, request);
    }

    @DeleteMapping("/{id}/episodes/{episodeId}")
    public AdminDramaResponse deleteEpisode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @PathVariable String episodeId
    ) {
        authService.requirePermission(authorization, legacyToken, "content");
        return dramaService.archiveEpisode(id, episodeId);
    }
}
