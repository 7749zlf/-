package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shortvideo.backend.admin.dto.AdminOrderRequest;
import com.shortvideo.backend.admin.dto.AdminOrderEventResponse;
import com.shortvideo.backend.admin.dto.AdminOrderResponse;
import com.shortvideo.backend.admin.dto.AdminRefundRequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOrderManagementService {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter ORDER_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbc;
    private final AdminAuditService auditService;

    public AdminOrderManagementService(JdbcTemplate jdbc, AdminAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<AdminOrderResponse> listOrders(String keyword) {
        String search = safe(keyword).toLowerCase(Locale.ROOT);
        List<OrderDisplayRow> rows = new ArrayList<>();
        rows.addAll(orderRows());
        rows.addAll(paymentRows());

        return rows.stream()
                .filter((row) -> matches(row.order(), search))
                .sorted(Comparator.comparing(
                        OrderDisplayRow::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .limit(300)
                .map(OrderDisplayRow::order)
                .toList();
    }

    public AdminOrderResponse getOrder(String id) {
        return listOrders("").stream()
                .filter((order) -> order.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    @Transactional
    public AdminOrderResponse createOrder(AdminOrderRequest request) {
        long userId = resolveUserId(request == null ? null : request.user(), 0L);
        long dramaId = resolveDramaId(request == null ? null : request.drama(), 0L);
        String id = nextOrderId();
        String item = text(request == null ? null : request.item(), "后台补单");
        BigDecimal amount = positiveAmount(request == null ? null : request.amount(), BigDecimal.ZERO);
        String method = text(request == null ? null : request.method(), "余额");
        String status = toInternalStatus(request == null ? null : request.status(), "PENDING");
        Timestamp paidAt = "PAID".equals(status) ? Timestamp.valueOf(LocalDateTime.now()) : null;

        jdbc.update("""
                INSERT INTO orders
                (id, user_id, drama_id, storyline_id, title, amount, payment_method, payment_method_key, status, paid_at)
                VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?)
                """, id, userId, dramaId, item, amount, method, methodKey(method), status, paidAt);

        if ("PAID".equals(status)) {
            addPaidAmount(userId, amount);
        }
        upsertControl(
                id,
                toInternalRisk(request == null ? null : request.risk(), "LOW"),
                toInternalEntitlement(request == null ? null : request.entitlement(), defaultEntitlement(status)),
                text(request == null ? null : request.note(), "运营手动创建订单")
        );
        return getOrder(id);
    }

    @Transactional
    public AdminOrderResponse updateOrder(String id, AdminOrderRequest request) {
        OrderRow current = orderRow(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        ControlRow control = controlRow(id).orElse(new ControlRow("LOW", defaultEntitlement(current.status()), ""));
        long userId = resolveUserId(request == null ? null : request.user(), current.userId());
        long dramaId = resolveDramaId(request == null ? null : request.drama(), current.dramaId());
        String item = text(request == null ? null : request.item(), current.title());
        BigDecimal amount = request != null && request.amount() != null
                ? positiveAmount(request.amount(), current.amount())
                : current.amount();
        String method = text(request == null ? null : request.method(), current.method());
        String status = toInternalStatus(request == null ? null : request.status(), current.status());
        ensureStatusTransition(current.status(), status);
        if (("PAID".equals(current.status()) || "REFUNDED".equals(current.status()))
                && (userId != current.userId()
                || dramaId != current.dramaId()
                || amount.compareTo(current.amount()) != 0
                || !safe(method).equals(safe(current.method())))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Paid order financial details are immutable");
        }
        if ("REFUNDED".equals(status) && !"REFUNDED".equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the refund action to refund a paid order");
        }

        jdbc.update("""
                UPDATE orders
                SET user_id = ?, drama_id = ?, title = ?, amount = ?,
                    payment_method = ?, payment_method_key = ?, status = ?,
                    paid_at = CASE
                        WHEN ? = 'PAID' AND paid_at IS NULL THEN CURRENT_TIMESTAMP
                        ELSE paid_at
                    END
                WHERE id = ?
                """, userId, dramaId, item, amount, method, methodKey(method), status, status, id);
        applyPaidAmountChange(current.userId(), current.amount(), current.status(), userId, amount, status);
        syncEntitlementForStatus(id, status);
        upsertControl(
                id,
                toInternalRisk(request == null ? null : request.risk(), control.risk()),
                toInternalEntitlement(request == null ? null : request.entitlement(), control.entitlement()),
                request != null && request.note() != null ? safe(request.note()) : control.note()
        );
        return getOrder(id);
    }

    @Transactional
    public AdminOrderResponse updateStatus(String id, String statusText) {
        return updateStatus(id, statusText, "system");
    }

    @Transactional
    public AdminOrderResponse updateStatus(String id, String statusText, String actorUsername) {
        String status = toInternalStatus(statusText, "PENDING");
        if ("REFUNDED".equals(status)) {
            return refundOrder(id, "运营后台退款", actorUsername);
        }
        if (orderRow(id).isPresent()) {
            String entitlement = "PAID".equals(status) ? "已发放" : "REFUNDED".equals(status) ? "已回收" : null;
            String risk = "PAID".equals(status) ? "低" : null;
            return updateOrder(id, new AdminOrderRequest(null, null, null, null, null, statusText, risk, entitlement, null));
        }

        if (paymentExists(id)) {
            String currentStatus = toInternalStatus(paymentStatus(id), "PENDING");
            ensureStatusTransition(currentStatus, status);
            if ("PAID".equals(status)) {
                finalizePayment(id);
            } else {
                jdbc.update("""
                        UPDATE h5_payments
                        SET status = ?,
                            paid_at = CASE WHEN ? = 'PAID' THEN CURRENT_TIMESTAMP ELSE paid_at END
                        WHERE id = ?
                        """, status, status, id);
            }
            syncEntitlementForStatus(id, status);
            ControlRow control = controlRow(id).orElse(new ControlRow("LOW", defaultEntitlement(status), ""));
            upsertControl(
                    id,
                    "PAID".equals(status) ? "LOW" : control.risk(),
                    "PAID".equals(status) ? "GRANTED" : "REFUNDED".equals(status) ? "REVOKED" : control.entitlement(),
                    control.note()
            );
            return getOrder(id);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id);
    }

    public AdminOrderResponse refundOrder(String id, String reason, String actorUsername) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Refund requires an H5 refund request and admin approval");
    }

    public List<AdminRefundRequestResponse> listRefundRequests(String status) {
        String filter = normalizeRefundStatus(status);
        return jdbc.query("""
                SELECT r.id, r.order_id, r.amount, r.reason, r.status,
                       r.review_reason, r.reviewer_username, r.created_at, r.reviewed_at,
                       u.nickname, u.device_id
                FROM h5_refund_requests r
                LEFT JOIN app_users u ON u.id = r.user_id
                WHERE (? = '' OR r.status = ?)
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT 200
                """, (rs, rowNum) -> new AdminRefundRequestResponse(
                rs.getString("id"),
                rs.getString("order_id"),
                text(repair(rs.getString("nickname")), text(rs.getString("device_id"), "U")),
                money(rs.getBigDecimal("amount")),
                repair(rs.getString("reason")),
                toDisplayRefundStatus(rs.getString("status")),
                repair(rs.getString("review_reason")),
                repair(rs.getString("reviewer_username")),
                timeText(toLocalDateTime(rs.getTimestamp("created_at"))),
                timeText(toLocalDateTime(rs.getTimestamp("reviewed_at")))
        ), filter, filter);
    }

    @Transactional
    public AdminRefundRequestResponse approveRefundRequest(String requestId, String reviewReason, String actorUsername) {
        RefundRequestRow request = refundRequestRow(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request not found"));
        if ("COMPLETED".equals(request.status())) {
            return refundRequestResponse(request);
        }
        if (!"PENDING_REVIEW".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund request is not pending review");
        }

        String actor = text(actorUsername, "system");
        String decisionReason = text(reviewReason, "审批通过");
        int updated = jdbc.update("""
                UPDATE h5_refund_requests
                SET status = 'APPROVED', review_reason = ?, reviewer_username = ?
                WHERE id = ? AND status = 'PENDING_REVIEW'
                """, decisionReason, actor, request.id());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund request has already been processed");
        }
        recordOrderEvent(request.orderId(), "REFUND_APPROVED", "PAID", "PAID", request.amount(), decisionReason, actor);
        executeApprovedRefund(request.orderId(), request.reason(), actor);
        jdbc.update("""
                UPDATE h5_refund_requests
                SET status = 'COMPLETED', reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.id());
        return refundRequestById(request.id());
    }

    @Transactional
    public AdminRefundRequestResponse rejectRefundRequest(String requestId, String reviewReason, String actorUsername) {
        RefundRequestRow request = refundRequestRow(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request not found"));
        if ("REJECTED".equals(request.status())) {
            return refundRequestResponse(request);
        }
        if (!"PENDING_REVIEW".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund request is not pending review");
        }

        String actor = text(actorUsername, "system");
        String decisionReason = text(reviewReason, "审批驳回");
        int updated = jdbc.update("""
                UPDATE h5_refund_requests
                SET status = 'REJECTED', review_reason = ?, reviewer_username = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING_REVIEW'
                """, decisionReason, actor, request.id());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund request has already been processed");
        }
        recordOrderEvent(request.orderId(), "REFUND_REJECTED", "PAID", "PAID", request.amount(), decisionReason, actor);
        return refundRequestById(request.id());
    }

    @Transactional
    private AdminOrderResponse executeApprovedRefund(String id, String reason, String actorUsername) {
        String orderId = safe(id);
        String refundReason = text(reason, "运营后台退款");
        String actor = text(actorUsername, "system");

        if (paymentExists(orderId)) {
            return refundPayment(orderId, refundReason, actor);
        }

        OrderRow current = orderRow(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        String currentStatus = toInternalStatus(current.status(), "PENDING");
        if ("REFUNDED".equals(currentStatus)) {
            return getOrder(orderId);
        }
        if (!"PAID".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only paid orders can be refunded: " + orderId);
        }

        int updated = jdbc.update(
                "UPDATE orders SET status = 'REFUNDED' WHERE id = ? AND status = 'PAID'",
                orderId);
        if (updated == 0) {
            return executeApprovedRefund(orderId, refundReason, actor);
        }
        subtractPaidAmount(current.userId(), current.amount());
        revokeOrderEntitlement(orderId);
        recordOrderEvent(orderId, "REFUNDED", "PAID", "REFUNDED", current.amount(), refundReason, actor);
        auditService.record(
                "ORDER_REFUNDED",
                "ORDER",
                orderId,
                java.util.Map.of("detail", refundReason, "amount", current.amount(), "actor", actor));
        return getOrder(orderId);
    }

    private Optional<RefundRequestRow> refundRequestRow(String requestId) {
        return jdbc.query("""
                SELECT id, order_id, amount, reason, status, review_reason,
                       reviewer_username, created_at, reviewed_at
                FROM h5_refund_requests
                WHERE id = ?
                """, (rs, rowNum) -> new RefundRequestRow(
                rs.getString("id"),
                rs.getString("order_id"),
                money(rs.getBigDecimal("amount")),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("review_reason"),
                rs.getString("reviewer_username"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("reviewed_at")
        ), requestId).stream().findFirst();
    }

    private AdminRefundRequestResponse refundRequestById(String requestId) {
        return jdbc.query("""
                SELECT r.id, r.order_id, r.amount, r.reason, r.status,
                       r.review_reason, r.reviewer_username, r.created_at, r.reviewed_at,
                       u.nickname, u.device_id
                FROM h5_refund_requests r
                LEFT JOIN app_users u ON u.id = r.user_id
                WHERE r.id = ?
                """, (rs, rowNum) -> new AdminRefundRequestResponse(
                rs.getString("id"),
                rs.getString("order_id"),
                text(repair(rs.getString("nickname")), text(rs.getString("device_id"), "U")),
                money(rs.getBigDecimal("amount")),
                repair(rs.getString("reason")),
                toDisplayRefundStatus(rs.getString("status")),
                repair(rs.getString("review_reason")),
                repair(rs.getString("reviewer_username")),
                timeText(toLocalDateTime(rs.getTimestamp("created_at"))),
                timeText(toLocalDateTime(rs.getTimestamp("reviewed_at")))
        ), requestId).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request not found"));
    }

    private AdminRefundRequestResponse refundRequestResponse(RefundRequestRow request) {
        return refundRequestById(request.id());
    }

    private String normalizeRefundStatus(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        return switch (value) {
            case "待审批", "PENDING_REVIEW" -> "PENDING_REVIEW";
            case "审批通过", "APPROVED" -> "APPROVED";
            case "已退款", "COMPLETED" -> "COMPLETED";
            case "已驳回", "REJECTED" -> "REJECTED";
            case "处理失败", "FAILED" -> "FAILED";
            default -> "";
        };
    }

    private String toDisplayRefundStatus(String status) {
        return switch (normalizeRefundStatus(status)) {
            case "PENDING_REVIEW" -> "待审批";
            case "APPROVED" -> "审批通过";
            case "COMPLETED" -> "已退款";
            case "REJECTED" -> "已驳回";
            case "FAILED" -> "处理失败";
            default -> "未知";
        };
    }

    public List<AdminOrderEventResponse> listEvents(String id) {
        String orderId = safe(id);
        if (!paymentExists(orderId) && !orderRow(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId);
        }
        return jdbc.query("""
                SELECT id, order_id, event_type, from_status, to_status, amount,
                       reason, actor_username, created_at
                FROM admin_order_events
                WHERE order_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 80
                """, (rs, rowNum) -> new AdminOrderEventResponse(
                rs.getLong("id"),
                rs.getString("order_id"),
                rs.getString("event_type"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getBigDecimal("amount"),
                repair(rs.getString("reason")),
                repair(rs.getString("actor_username")),
                timeText(toLocalDateTime(rs.getTimestamp("created_at")))
        ), orderId);
    }

    private AdminOrderResponse refundPayment(String paymentId, String reason, String actorUsername) {
        PaymentRow payment = paymentRow(paymentId);
        String currentStatus = toInternalStatus(payment.status(), "PENDING");
        if ("REFUNDED".equals(currentStatus)) {
            return getOrder(paymentId);
        }
        if (!"PAID".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only paid orders can be refunded: " + paymentId);
        }

        OrderRow currentOrder = orderRow(paymentId).orElse(null);
        BigDecimal amount = currentOrder == null ? payment.amount() : currentOrder.amount();
        int updated = jdbc.update(
                "UPDATE h5_payments SET status = 'REFUNDED' WHERE id = ? AND status = 'PAID'",
                paymentId);
        if (updated == 0) {
            return executeApprovedRefund(paymentId, reason, actorUsername);
        }

        if (currentOrder == null) {
            subtractPaidAmount(payment.userId(), amount);
        } else {
            jdbc.update("UPDATE orders SET status = 'REFUNDED' WHERE id = ? AND status = 'PAID'", paymentId);
            revokeOrderEntitlement(paymentId);
            subtractPaidAmount(currentOrder.userId(), currentOrder.amount());
        }
        restoreBalanceIfNeeded(payment.userId(), payment.methodKey(), amount);

        String refundTradeNo = "refund-local-" + paymentId;
        recordPaymentEvent(paymentId, "REFUNDED", "REFUNDED", refundTradeNo, reason);
        recordOrderEvent(paymentId, "REFUNDED", "PAID", "REFUNDED", amount, reason, actorUsername);
        auditService.record(
                "ORDER_REFUNDED",
                "ORDER",
                paymentId,
                java.util.Map.of("detail", reason, "amount", amount, "actor", actorUsername));
        return getOrder(paymentId);
    }

    @Transactional
    public AdminOrderResponse resendEntitlement(String id) {
        Optional<OrderRow> existingOrder = orderRow(id);
        if (existingOrder.isPresent()) {
            if (!"PAID".equals(toInternalStatus(existingOrder.get().status(), "PENDING"))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only paid orders can resend entitlement: " + id);
            }
            grantOrderEntitlement(id);
        } else if (paymentExists(id)) {
            if (!"PAID".equals(toInternalStatus(paymentStatus(id), "PENDING"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment is not paid yet: " + id);
            }
            finalizePayment(id);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id);
        }

        ControlRow control = controlRow(id).orElse(new ControlRow("LOW", "GRANTED", ""));
        upsertControl(id, control.risk(), "RESENT", "运营手动补发权益");
        return getOrder(id);
    }

    private List<OrderDisplayRow> orderRows() {
        return jdbc.query("""
                SELECT o.id, o.user_id, o.drama_id, o.title, o.amount,
                       o.payment_method, o.payment_method_key, o.status, o.created_at, o.paid_at,
                       u.nickname, u.device_id, d.title AS drama_title,
                       c.risk, c.entitlement, c.note
                FROM orders o
                LEFT JOIN app_users u ON u.id = o.user_id
                LEFT JOIN dramas d ON d.id = o.drama_id
                LEFT JOIN admin_order_controls c ON c.order_id = o.id
                ORDER BY o.created_at DESC
                LIMIT 500
                """, (rs, rowNum) -> {
            LocalDateTime createdAt = toLocalDateTime(rs.getTimestamp("created_at"));
            String status = rs.getString("status");
            AdminOrderResponse response = toResponse(
                    rs.getString("id"),
                    rs.getLong("user_id"),
                    rs.getLong("drama_id"),
                    rs.getString("title"),
                    rs.getBigDecimal("amount"),
                    rs.getString("payment_method"),
                    status,
                    createdAt,
                    rs.getString("nickname"),
                    rs.getString("device_id"),
                    rs.getString("drama_title"),
                    rs.getString("risk"),
                    rs.getString("entitlement"),
                    rs.getString("note")
            );
            return new OrderDisplayRow(response, createdAt);
        });
    }

    private List<OrderDisplayRow> paymentRows() {
        return jdbc.query("""
                SELECT p.id, p.user_id, p.drama_id,
                       COALESCE(s.name, '支付订单') AS title,
                       p.amount, p.method_name, p.method_key, p.status, p.created_at, p.paid_at,
                       u.nickname, u.device_id, d.title AS drama_title,
                       c.risk, c.entitlement, c.note
                FROM h5_payments p
                LEFT JOIN orders o ON o.id = p.id
                LEFT JOIN app_users u ON u.id = p.user_id
                LEFT JOIN dramas d ON d.id = p.drama_id
                LEFT JOIN storylines s ON s.id = p.storyline_id
                LEFT JOIN admin_order_controls c ON c.order_id = p.id
                WHERE o.id IS NULL
                ORDER BY p.created_at DESC
                LIMIT 500
                """, (rs, rowNum) -> {
            LocalDateTime createdAt = toLocalDateTime(rs.getTimestamp("created_at"));
            AdminOrderResponse response = toResponse(
                    rs.getString("id"),
                    rs.getLong("user_id"),
                    rs.getLong("drama_id"),
                    rs.getString("title"),
                    rs.getBigDecimal("amount"),
                    rs.getString("method_name"),
                    rs.getString("status"),
                    createdAt,
                    rs.getString("nickname"),
                    rs.getString("device_id"),
                    rs.getString("drama_title"),
                    rs.getString("risk"),
                    rs.getString("entitlement"),
                    rs.getString("note")
            );
            return new OrderDisplayRow(response, createdAt);
        });
    }

    private AdminOrderResponse toResponse(
            String id,
            long userId,
            long dramaId,
            String item,
            BigDecimal amount,
            String method,
            String status,
            LocalDateTime createdAt,
            String nickname,
            String deviceId,
            String dramaTitle,
            String risk,
            String entitlement,
            String note
    ) {
        String internalStatus = toInternalStatus(status, "PENDING");
        String user = text(repair(nickname), text(deviceId, "U" + userId));
        String drama = text(repair(dramaTitle), "D" + dramaId);
        return new AdminOrderResponse(
                id,
                user,
                drama,
                text(repair(item), "支付订单"),
                money(amount),
                text(repair(method), "余额"),
                toDisplayStatus(internalStatus),
                toDisplayRisk(risk),
                createdAt == null ? "" : createdAt.format(TIME_TEXT),
                toDisplayEntitlement(entitlement == null || entitlement.isBlank() ? defaultEntitlement(internalStatus) : entitlement),
                note == null ? "" : repair(note),
                userId,
                dramaId
        );
    }

    private Optional<OrderRow> orderRow(String id) {
        return jdbc.query("""
                SELECT id, user_id, drama_id, title, amount, payment_method, payment_method_key, status, paid_at
                FROM orders
                WHERE id = ?
                """, (rs, rowNum) -> new OrderRow(
                rs.getString("id"),
                rs.getLong("user_id"),
                rs.getLong("drama_id"),
                rs.getString("title"),
                money(rs.getBigDecimal("amount")),
                rs.getString("payment_method"),
                rs.getString("payment_method_key"),
                rs.getString("status"),
                rs.getTimestamp("paid_at")
        ), id).stream().findFirst();
    }

    private Optional<ControlRow> controlRow(String id) {
        return jdbc.query("""
                SELECT risk, entitlement, note
                FROM admin_order_controls
                WHERE order_id = ?
                """, (rs, rowNum) -> new ControlRow(
                rs.getString("risk"),
                rs.getString("entitlement"),
                rs.getString("note")
        ), id).stream().findFirst();
    }

    private void upsertControl(String id, String risk, String entitlement, String note) {
        jdbc.update("""
                INSERT INTO admin_order_controls (order_id, risk, entitlement, note)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    risk = VALUES(risk),
                    entitlement = VALUES(entitlement),
                    note = VALUES(note)
                """, id, risk, entitlement, note == null ? "" : note);
    }

    private long resolveUserId(String userText, long fallback) {
        String user = safe(userText);
        if (!user.isBlank()) {
            Matcher matcher = DIGITS.matcher(user);
            if (matcher.find()) {
                try {
                    long id = Long.parseLong(matcher.group());
                    if (exists("SELECT COUNT(*) FROM app_users WHERE id = ?", id)) {
                        return id;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to text lookup.
                }
            }
            Long matched = jdbc.query("""
                    SELECT id
                    FROM app_users
                    WHERE device_id = ? OR nickname = ?
                    ORDER BY id
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getLong("id"), user, user).stream().findFirst().orElse(null);
            if (matched != null) {
                return matched;
            }
            String deviceId = "admin-order-" + UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO app_users (device_id, nickname, level, status, last_active_at)
                    VALUES (?, ?, '新用户', 'NORMAL', CURRENT_TIMESTAMP)
                    """, deviceId, user);
            Long created = jdbc.queryForObject("SELECT id FROM app_users WHERE device_id = ?", Long.class, deviceId);
            if (created != null) {
                return created;
            }
        }
        if (fallback > 0) {
            return fallback;
        }
        return jdbc.query("SELECT id FROM app_users ORDER BY id LIMIT 1", (rs, rowNum) -> rs.getLong("id"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No user is available"));
    }

    private long resolveDramaId(String dramaText, long fallback) {
        String drama = safe(dramaText);
        if (!drama.isBlank()) {
            Matcher matcher = DIGITS.matcher(drama);
            if (matcher.find()) {
                try {
                    long id = Long.parseLong(matcher.group());
                    if (exists("SELECT COUNT(*) FROM dramas WHERE id = ?", id)) {
                        return id;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to title lookup.
                }
            }
            Long matched = jdbc.query("""
                    SELECT id
                    FROM dramas
                    WHERE title = ?
                    ORDER BY sort_order, id
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getLong("id"), drama).stream().findFirst().orElse(null);
            if (matched != null) {
                return matched;
            }
        }
        if (fallback > 0) {
            return fallback;
        }
        return jdbc.query("SELECT id FROM dramas ORDER BY sort_order, id LIMIT 1", (rs, rowNum) -> rs.getLong("id"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No drama is available"));
    }

    private boolean paymentExists(String id) {
        return exists("SELECT COUNT(*) FROM h5_payments WHERE id = ?", id);
    }

    private String paymentStatus(String id) {
        return jdbc.query("""
                SELECT status
                FROM h5_payments
                WHERE id = ?
                """, (rs, rowNum) -> rs.getString("status"), id).stream().findFirst().orElse("");
    }

    private PaymentRow paymentRow(String id) {
        return jdbc.query("""
                SELECT id, user_id, amount, method_key, status
                FROM h5_payments
                WHERE id = ?
                """, (rs, rowNum) -> new PaymentRow(
                rs.getString("id"),
                rs.getLong("user_id"),
                money(rs.getBigDecimal("amount")),
                rs.getString("method_key"),
                rs.getString("status")
        ), id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    private void finalizePayment(String paymentId) {
        PaymentRow payment = paymentRow(paymentId);
        if (!"PAID".equalsIgnoreCase(payment.status())) {
            debitBalanceIfNeeded(payment);
        }
        jdbc.update("""
                UPDATE h5_payments
                SET status = 'PAID', paid_at = COALESCE(paid_at, CURRENT_TIMESTAMP)
                WHERE id = ?
                """, paymentId);
        int insertedOrders = jdbc.update("""
                INSERT IGNORE INTO orders
                (id, user_id, drama_id, storyline_id, title, amount, payment_method, payment_method_key, status, paid_at)
                SELECT p.id, p.user_id, p.drama_id, p.storyline_id,
                       COALESCE(s.name, '支付订单'), p.amount, p.method_name, p.method_key, 'PAID', CURRENT_TIMESTAMP
                FROM h5_payments p
                LEFT JOIN storylines s ON s.id = p.storyline_id
                WHERE p.id = ?
                """, paymentId);
        grantOrderEntitlement(paymentId);
        if (insertedOrders > 0) {
            jdbc.update("""
                    UPDATE app_users u
                    JOIN h5_payments p ON p.user_id = u.id
                    SET u.paid_amount = u.paid_amount + p.amount
                    WHERE p.id = ?
                    """, paymentId);
        }
    }

    private void grantOrderEntitlement(String orderId) {
        jdbc.update("""
                INSERT IGNORE INTO user_unlocks (user_id, drama_id, storyline_id, order_id)
                SELECT user_id, drama_id, storyline_id, id
                FROM orders
                WHERE id = ? AND storyline_id IS NOT NULL AND storyline_id <> ''
                """, orderId);
    }

    private void revokeOrderEntitlement(String orderId) {
        jdbc.update("DELETE FROM user_unlocks WHERE order_id = ?", orderId);
    }

    private void recordOrderEvent(
            String orderId,
            String eventType,
            String fromStatus,
            String toStatus,
            BigDecimal amount,
            String reason,
            String actorUsername
    ) {
        jdbc.update("""
                INSERT INTO admin_order_events
                (order_id, event_type, from_status, to_status, amount, reason, actor_username)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                eventType,
                fromStatus,
                toStatus,
                money(amount),
                text(reason, ""),
                text(actorUsername, "system"));
    }

    private void recordPaymentEvent(
            String paymentId,
            String eventType,
            String status,
            String providerTradeNo,
            String message
    ) {
        jdbc.update("""
                INSERT INTO h5_payment_events (payment_id, event_type, status, provider_trade_no, message)
                VALUES (?, ?, ?, NULLIF(?, ''), ?)
                """,
                paymentId,
                eventType,
                status,
                safe(providerTradeNo),
                text(message, ""));
    }

    private void syncEntitlementForStatus(String orderId, String status) {
        if ("PAID".equals(status)) {
            grantOrderEntitlement(orderId);
        } else {
            revokeOrderEntitlement(orderId);
        }
    }

    private void ensureStatusTransition(String currentStatus, String nextStatus) {
        String current = toInternalStatus(currentStatus, "PENDING");
        String next = toInternalStatus(nextStatus, current);
        if (current.equals(next)) {
            return;
        }
        if ("REFUNDED".equals(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Refunded orders are final");
        }
        if ("FAILED".equals(current) || "CANCELLED".equals(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Failed or cancelled payments require a new payment");
        }
        if ("PAID".equals(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Paid orders can only change through the approved refund flow");
        }
    }

    private void applyPaidAmountChange(
            long oldUserId,
            BigDecimal oldAmount,
            String oldStatus,
            long newUserId,
            BigDecimal newAmount,
            String newStatus
    ) {
        if ("PAID".equals(toInternalStatus(oldStatus, ""))) {
            subtractPaidAmount(oldUserId, oldAmount);
        }
        if ("PAID".equals(toInternalStatus(newStatus, ""))) {
            addPaidAmount(newUserId, newAmount);
        }
    }

    private void addPaidAmount(long userId, BigDecimal amount) {
        jdbc.update("UPDATE app_users SET paid_amount = paid_amount + ? WHERE id = ?", money(amount), userId);
    }

    private void subtractPaidAmount(long userId, BigDecimal amount) {
        jdbc.update("UPDATE app_users SET paid_amount = GREATEST(paid_amount - ?, 0) WHERE id = ?", money(amount), userId);
    }

    private void debitBalanceIfNeeded(PaymentRow payment) {
        if (!isBalancePayment(payment.methodKey())) {
            return;
        }
        int updated = jdbc.update(
                "UPDATE app_users SET balance = balance - ? WHERE id = ? AND balance >= ?",
                payment.amount(), payment.userId(), payment.amount());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient balance");
        }
    }

    private void restoreBalanceIfNeeded(long userId, String methodKey, BigDecimal amount) {
        if (isBalancePayment(methodKey)) {
            jdbc.update("UPDATE app_users SET balance = balance + ? WHERE id = ?", money(amount), userId);
        }
    }

    private boolean isBalancePayment(String methodKey) {
        String normalized = safe(methodKey).toLowerCase(Locale.ROOT);
        return "balance".equals(normalized) || "wallet".equals(normalized);
    }

    private String nextOrderId() {
        String id;
        do {
            id = "O" + LocalDateTime.now().format(ORDER_ID_TIME)
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        } while (exists("SELECT COUNT(*) FROM orders WHERE id = ?", id)
                || exists("SELECT COUNT(*) FROM h5_payments WHERE id = ?", id));
        return id;
    }

    private boolean matches(AdminOrderResponse order, String search) {
        if (search.isBlank()) {
            return true;
        }
        String haystack = String.join(" ",
                safe(order.id()),
                safe(order.user()),
                safe(order.drama()),
                safe(order.item()),
                safe(order.method()),
                safe(order.status())
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(search);
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private String toInternalStatus(String status, String fallback) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        if (value.equals("已支付") || value.equals("PAID") || value.equals("SUCCESS")) {
            return "PAID";
        }
        if (value.equals("处理中") || value.equals("PENDING") || value.equals("PROCESSING")) {
            return "PENDING";
        }
        if (value.equals("风控中") || value.equals("RISK") || value.equals("REVIEWING")) {
            return "RISK";
        }
        if (value.equals("已退款") || value.equals("REFUNDED") || value.equals("REFUND")) {
            return "REFUNDED";
        }
        if (value.equals("支付失败") || value.equals("FAILED")) {
            return "FAILED";
        }
        if (value.equals("已取消") || value.equals("CANCELLED")) {
            return "CANCELLED";
        }
        return fallback;
    }

    private String toDisplayStatus(String status) {
        return switch (toInternalStatus(status, "PENDING")) {
            case "PAID" -> "已支付";
            case "RISK" -> "风控中";
            case "REFUNDED" -> "已退款";
            case "FAILED" -> "支付失败";
            case "CANCELLED" -> "已取消";
            default -> "处理中";
        };
    }

    private String toInternalRisk(String risk, String fallback) {
        String value = safe(risk).toUpperCase(Locale.ROOT);
        if (value.equals("低") || value.equals("LOW")) return "LOW";
        if (value.equals("中") || value.equals("MEDIUM") || value.equals("MID")) return "MEDIUM";
        if (value.equals("高") || value.equals("HIGH")) return "HIGH";
        return fallback;
    }

    private String toDisplayRisk(String risk) {
        return switch (toInternalRisk(risk, "LOW")) {
            case "MEDIUM" -> "中";
            case "HIGH" -> "高";
            default -> "低";
        };
    }

    private String toInternalEntitlement(String entitlement, String fallback) {
        String value = safe(entitlement).toUpperCase(Locale.ROOT);
        if (value.equals("待发放") || value.equals("PENDING")) return "PENDING";
        if (value.equals("已发放") || value.equals("GRANTED")) return "GRANTED";
        if (value.equals("已补发") || value.equals("RESENT")) return "RESENT";
        if (value.equals("冻结") || value.equals("FROZEN")) return "FROZEN";
        if (value.equals("已回收") || value.equals("REVOKED")) return "REVOKED";
        return fallback;
    }

    private String toDisplayEntitlement(String entitlement) {
        return switch (toInternalEntitlement(entitlement, "PENDING")) {
            case "GRANTED" -> "已发放";
            case "RESENT" -> "已补发";
            case "FROZEN" -> "冻结";
            case "REVOKED" -> "已回收";
            default -> "待发放";
        };
    }

    private String defaultEntitlement(String status) {
        return switch (toInternalStatus(status, "PENDING")) {
            case "PAID" -> "GRANTED";
            case "REFUNDED" -> "REVOKED";
            case "FAILED", "CANCELLED" -> "REVOKED";
            default -> "PENDING";
        };
    }

    private String methodKey(String method) {
        String value = safe(method);
        if (value.equalsIgnoreCase("PayPal")) return "paypal";
        if (value.equals("余额")) return "balance";
        if (value.equals("银行卡")) return "bank-card";
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "admin-manual" : normalized;
    }

    private BigDecimal positiveAmount(BigDecimal value, BigDecimal fallback) {
        BigDecimal amount = value == null ? fallback : value;
        return money(amount.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : amount);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String text(String value, String fallback) {
        String clean = safe(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String timeText(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_TEXT);
    }

    private record OrderDisplayRow(AdminOrderResponse order, LocalDateTime createdAt) {
    }

    private record OrderRow(
            String id,
            long userId,
            long dramaId,
            String title,
            BigDecimal amount,
            String method,
            String methodKey,
            String status,
            Timestamp paidAt
    ) {
    }

    private record ControlRow(String risk, String entitlement, String note) {
    }

    private record RefundRequestRow(
            String id,
            String orderId,
            BigDecimal amount,
            String reason,
            String status,
            String reviewReason,
            String reviewerUsername,
            Timestamp createdAt,
            Timestamp reviewedAt
    ) {
    }

    private record PaymentRow(
            String id,
            long userId,
            BigDecimal amount,
            String methodKey,
            String status
    ) {
    }
}
