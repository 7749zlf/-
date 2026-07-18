package com.shortvideo.backend.admin;

import com.shortvideo.backend.admin.dto.AdminFinanceResponse;
import com.shortvideo.backend.admin.dto.AdminReconciliationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/finance")
public class AdminFinanceController {

    private final AdminAuthService authService;
    private final AdminFinanceService financeService;

    public AdminFinanceController(AdminAuthService authService, AdminFinanceService financeService) {
        this.authService = authService;
        this.financeService = financeService;
    }

    @GetMapping("/summary")
    public AdminFinanceResponse summary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        authService.requirePermission(authorization, legacyToken, "finance");
        return financeService.summary();
    }

    @GetMapping("/reconciliation")
    public AdminReconciliationResponse reconciliation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        authService.requirePermission(authorization, legacyToken, "finance");
        return financeService.reconciliation(keyword, status);
    }
}
