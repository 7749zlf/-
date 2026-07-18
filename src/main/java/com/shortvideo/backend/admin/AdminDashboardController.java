package com.shortvideo.backend.admin;

import com.shortvideo.backend.admin.dto.AdminDashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminAuthService authService;
    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminAuthService authService, AdminDashboardService dashboardService) {
        this.authService = authService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public AdminDashboardResponse summary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        authService.requirePermission(authorization, legacyToken, "dashboard");
        return dashboardService.summary();
    }
}
