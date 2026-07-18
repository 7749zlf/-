package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminAuditLogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuthService authService;
    private final AdminAuditLogService auditLogService;

    public AdminAuditLogController(AdminAuthService authService, AdminAuditLogService auditLogService) {
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AdminAuditLogResponse> listLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        authService.requirePermission(authorization, legacyToken, "settings");
        return auditLogService.listLogs();
    }
}
