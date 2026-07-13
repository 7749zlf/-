package com.shortvideo.backend.admin;

import com.shortvideo.backend.admin.dto.AdminAuthResponse;
import com.shortvideo.backend.admin.dto.AdminLoginRequest;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.h5.dto.ApiOkResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AdminAuthResponse login(@RequestBody(required = false) AdminLoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AdminProfileResponse me(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken
    ) {
        return authService.current(authorization, legacyToken);
    }

    @PostMapping("/logout")
    public ApiOkResponse logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return new ApiOkResponse(true);
    }
}
