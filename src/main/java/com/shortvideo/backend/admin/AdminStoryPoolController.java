package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminStoryPoolRequest;
import com.shortvideo.backend.admin.dto.AdminStoryPoolResponse;
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
@RequestMapping("/api/admin/story-pools")
public class AdminStoryPoolController {

    private final AdminAuthService authService;
    private final AdminStoryPoolManagementService storyPoolService;

    public AdminStoryPoolController(AdminAuthService authService, AdminStoryPoolManagementService storyPoolService) {
        this.authService = authService;
        this.storyPoolService = storyPoolService;
    }

    @GetMapping
    public List<AdminStoryPoolResponse> listStoryPools(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String keyword
    ) {
        authService.requirePermission(authorization, legacyToken, "storyline");
        return storyPoolService.listStoryPools(keyword);
    }

    @GetMapping("/{id}")
    public AdminStoryPoolResponse storyPool(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id
    ) {
        authService.requirePermission(authorization, legacyToken, "storyline");
        return storyPoolService.getStoryPool(id);
    }

    @PostMapping
    public AdminStoryPoolResponse createStoryPool(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) AdminStoryPoolRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "storyline");
        return storyPoolService.createStoryPool(request);
    }

    @PutMapping("/{id}")
    public AdminStoryPoolResponse updateStoryPool(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminStoryPoolRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "storyline");
        return storyPoolService.updateStoryPool(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminStoryPoolResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminStoryPoolRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "storyline");
        return storyPoolService.updateStatus(id, request == null ? "暂停" : request.status());
    }
}
