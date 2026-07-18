package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminChannelRequest;
import com.shortvideo.backend.admin.dto.AdminChannelResponse;
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
@RequestMapping("/api/admin/channels")
public class AdminChannelController {

    private final AdminAuthService authService;
    private final AdminChannelManagementService channelService;

    public AdminChannelController(AdminAuthService authService, AdminChannelManagementService channelService) {
        this.authService = authService;
        this.channelService = channelService;
    }

    @GetMapping
    public List<AdminChannelResponse> listChannels(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String keyword
    ) {
        authService.requirePermission(authorization, legacyToken, "channels");
        return channelService.listChannels(keyword);
    }

    @GetMapping("/{id}")
    public AdminChannelResponse channel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id
    ) {
        authService.requirePermission(authorization, legacyToken, "channels");
        return channelService.getChannel(id);
    }

    @PostMapping
    public AdminChannelResponse createChannel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) AdminChannelRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "channels");
        return channelService.createChannel(request);
    }

    @PutMapping("/{id}")
    public AdminChannelResponse updateChannel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminChannelRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "channels");
        return channelService.updateChannel(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminChannelResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminChannelRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "channels");
        return channelService.updateStatus(id, request == null ? "观察中" : request.status());
    }
}
