package com.shortvideo.backend.admin;

import java.util.List;

import com.shortvideo.backend.admin.dto.AdminOrderRequest;
import com.shortvideo.backend.admin.dto.AdminOrderEventResponse;
import com.shortvideo.backend.admin.dto.AdminOrderRefundRequest;
import com.shortvideo.backend.admin.dto.AdminOrderResponse;
import com.shortvideo.backend.admin.dto.AdminRefundRequestResponse;
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
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminAuthService authService;
    private final AdminOrderManagementService orderService;

    public AdminOrderController(AdminAuthService authService, AdminOrderManagementService orderService) {
        this.authService = authService;
        this.orderService = orderService;
    }

    @GetMapping
    public List<AdminOrderResponse> listOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String keyword
    ) {
        authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.listOrders(keyword);
    }

    @GetMapping("/{id}")
    public AdminOrderResponse order(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id
    ) {
        authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.getOrder(id);
    }

    @GetMapping("/{id}/events")
    public List<AdminOrderEventResponse> orderEvents(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id
    ) {
        authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.listEvents(id);
    }

    @GetMapping("/refund-requests")
    public List<AdminRefundRequestResponse> refundRequests(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestParam(required = false) String status
    ) {
        authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.listRefundRequests(status);
    }

    @PostMapping
    public AdminOrderResponse createOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @RequestBody(required = false) AdminOrderRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.createOrder(request);
    }

    @PutMapping("/{id}")
    public AdminOrderResponse updateOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminOrderRequest request
    ) {
        authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.updateOrder(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminOrderResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id,
            @RequestBody(required = false) AdminOrderRequest request
    ) {
        var admin = authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.updateStatus(id, request == null ? "处理中" : request.status(), admin.username());
    }

    @PostMapping("/refund-requests/{requestId}/approve")
    public AdminRefundRequestResponse approveRefundRequest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String requestId,
            @RequestBody(required = false) AdminOrderRefundRequest request
    ) {
        var admin = authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.approveRefundRequest(
                requestId,
                request == null ? null : request.reason(),
                admin.username());
    }

    @PostMapping("/refund-requests/{requestId}/reject")
    public AdminRefundRequestResponse rejectRefundRequest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String requestId,
            @RequestBody(required = false) AdminOrderRefundRequest request
    ) {
        var admin = authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.rejectRefundRequest(
                requestId,
                request == null ? null : request.reason(),
                admin.username());
    }

    @PatchMapping("/{id}/entitlement/resend")
    public AdminOrderResponse resendEntitlement(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Token", required = false) String legacyToken,
            @PathVariable String id
    ) {
        authService.requirePermission(authorization, legacyToken, "orders");
        return orderService.resendEntitlement(id);
    }
}
