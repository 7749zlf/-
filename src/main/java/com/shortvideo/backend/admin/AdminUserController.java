package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminUserRequest;
import com.shortvideo.backend.admin.dto.AdminUserResponse;
import com.shortvideo.backend.admin.dto.AdminUserStatusRequest;
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
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminAuthService authService;
    private final AdminUserManagementService userService;

    public AdminUserController(AdminAuthService authService, AdminUserManagementService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String keyword
    ) {
        authService.requireAdmin(authorization, legacyToken);
        return userService.listUsers(keyword);
    }

    @PostMapping
    public AdminUserResponse createUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) AdminUserRequest request
    ) {
        authService.requireAdmin(authorization, legacyToken);
        return userService.createUser(request);
    }

    @PutMapping("/{id}")
    public AdminUserResponse updateUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminUserRequest request
    ) {
        authService.requireAdmin(authorization, legacyToken);
        return userService.updateUser(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminUserResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminUserStatusRequest request
    ) {
        authService.requireAdmin(authorization, legacyToken);
        return userService.updateStatus(id, request == null ? "NORMAL" : request.status());
    }
}
