package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminPermissionResponse;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.admin.dto.AdminRoleRequest;
import com.shortvideo.backend.admin.dto.AdminRoleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {

    private final AdminAuthService authService;
    private final AdminRoleManagementService roleService;

    public AdminRoleController(AdminAuthService authService, AdminRoleManagementService roleService) {
        this.authService = authService;
        this.roleService = roleService;
    }

    @GetMapping
    public List<AdminRoleResponse> listRoles(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        authService.requirePermission(authorization, legacyToken, "roles");
        return roleService.listRoles();
    }

    @GetMapping("/permissions")
    public List<AdminPermissionResponse> listPermissions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        authService.requirePermission(authorization, legacyToken, "roles");
        return roleService.listPermissions();
    }

    @PostMapping
    public AdminRoleResponse createRole(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) AdminRoleRequest request
    ) {
        AdminProfileResponse actor = authService.requirePermission(authorization, legacyToken, "roles");
        return roleService.createRole(request, actor);
    }

    @PutMapping("/{id}")
    public AdminRoleResponse updateRole(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminRoleRequest request
    ) {
        AdminProfileResponse actor = authService.requirePermission(authorization, legacyToken, "roles");
        return roleService.updateRole(id, request, actor);
    }

    @PatchMapping("/{id}/status")
    public AdminRoleResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminRoleRequest request
    ) {
        AdminProfileResponse actor = authService.requirePermission(authorization, legacyToken, "roles");
        return roleService.updateStatus(id, request == null ? "已启用" : request.status(), actor);
    }
}
