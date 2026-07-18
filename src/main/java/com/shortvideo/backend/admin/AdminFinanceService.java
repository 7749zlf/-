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

import com.shortvideo.backend.admin.dto.AdminFinanceResponse;
import com.shortvideo.backend.admin.dto.AdminReconciliationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminFinanceService {

    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final AdminOrderManagementService orderService;

    public AdminFinanceService(JdbcTemplate jdbc, AdminOrderManagementService orderService) {
        this.jdbc = jdbc;
        this.orderService = orderService;
    }

    public AdminFinanceResponse summary() {
        List<AdminFinanceResponse.FinanceRow> rows = jdbc.query("""
                SELECT COALESCE(d.title, '未关联剧目') AS drama,
                       COALESCE(NULLIF(o.payment_method, ''), '真实订单') AS channel,
                       COALESCE(SUM(o.amount), 0) AS revenue,
                       COUNT(*) AS orders
                FROM orders o
                LEFT JOIN dramas d ON d.id = o.drama_id
                WHERE UPPER(o.status) = 'PAID'
                GROUP BY COALESCE(d.title, '未关联剧目'), COALESCE(NULLIF(o.payment_method, ''), '真实订单')
                ORDER BY revenue DESC
                LIMIT 12
                """, (rs, rowNum) -> {
            BigDecimal revenue = money(rs.getBigDecimal("revenue"));
            BigDecimal cost = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new AdminFinanceResponse.FinanceRow(
                    repair(rs.getString("drama")),
                    repair(rs.getString("channel")),
                    revenue,
                    cost,
                    rs.getInt("orders"),
                    roi(revenue, cost)
            );
        });

        List<AdminFinanceResponse.RecentOrder> recentOrders = orderService.listOrders("").stream()
                .limit(10)
                .map((order) -> new AdminFinanceResponse.RecentOrder(
                        order.id(),
                        order.user(),
                        order.item(),
                        moneyText(order.amount()),
                        order.status(),
                        order.time()
                ))
                .toList();

        return new AdminFinanceResponse(rows, recentOrders);
    }

    public AdminReconciliationResponse reconciliation(String keyword, String status) {
        String search = safe(keyword).toLowerCase(Locale.ROOT);
        String statusFilter = safe(status);
        List<ReconciliationRow> allRows = jdbc.query("""
                SELECT p.id AS payment_id,
                       p.user_id,
                       p.amount AS payment_amount,
                       p.method_name,
                       p.status AS payment_status,
                       p.provider_trade_no,
                       p.created_at,
                       p.paid_at,
                       o.id AS order_id,
                       o.amount AS order_amount,
                       o.status AS order_status,
                       u.nickname,
                       u.device_id,
                       COALESCE(event_stats.event_count, 0) AS event_count,
                       latest_event.event_type AS latest_event_type,
                       latest_event.created_at AS latest_event_at
                FROM h5_payments p
                LEFT JOIN orders o ON o.id = p.id
                LEFT JOIN app_users u ON u.id = p.user_id
                LEFT JOIN (
                    SELECT payment_id, COUNT(*) AS event_count
                    FROM h5_payment_events
                    GROUP BY payment_id
                ) event_stats ON event_stats.payment_id = p.id
                LEFT JOIN (
                    SELECT payment_id, event_type, created_at,
                           ROW_NUMBER() OVER (PARTITION BY payment_id ORDER BY created_at DESC, id DESC) AS rank_no
                    FROM h5_payment_events
                ) latest_event ON latest_event.payment_id = p.id AND latest_event.rank_no = 1
                ORDER BY p.created_at DESC, p.id DESC
                LIMIT 500
                """, (rs, rowNum) -> toReconciliationRow(
                rs.getString("payment_id"),
                rs.getString("order_id"),
                rs.getString("nickname"),
                rs.getString("device_id"),
                rs.getBigDecimal("payment_amount"),
                rs.getBigDecimal("order_amount"),
                rs.getString("payment_status"),
                rs.getString("order_status"),
                rs.getString("method_name"),
                rs.getString("provider_trade_no"),
                rs.getInt("event_count"),
                rs.getString("latest_event_type"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("paid_at")),
                toLocalDateTime(rs.getTimestamp("latest_event_at"))
        ));

        List<ReconciliationRow> filtered = allRows.stream()
                .filter(row -> matches(row, search))
                .filter(row -> statusFilter.isBlank()
                        || statusFilter.equals("全部")
                        || statusFilter.equals(row.response().reconciliationStatus()))
                .sorted(Comparator.comparing(
                        (ReconciliationRow row) -> row.response().createdAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        return new AdminReconciliationResponse(summary(filtered), filtered.stream()
                .map(ReconciliationRow::response)
                .toList());
    }

    private ReconciliationRow toReconciliationRow(
            String paymentId,
            String orderId,
            String nickname,
            String deviceId,
            BigDecimal paymentAmount,
            BigDecimal orderAmount,
            String paymentStatus,
            String orderStatus,
            String method,
            String providerTradeNo,
            int eventCount,
            String latestEventType,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            LocalDateTime latestEventAt
    ) {
        String payment = normalizeStatus(paymentStatus);
        String order = normalizeStatus(orderStatus);
        String reconciliationStatus = "异常";
        String mismatchReason = "";

        if ("PENDING".equals(payment) && (orderId == null || orderId.isBlank())) {
            reconciliationStatus = "待支付";
        } else if ("PAID".equals(payment) && "PAID".equals(order)
                && sameMoney(paymentAmount, orderAmount) && eventCount > 0) {
            reconciliationStatus = "已对账";
        } else if ("REFUNDED".equals(payment) && "REFUNDED".equals(order)
                && sameMoney(paymentAmount, orderAmount) && eventCount > 0) {
            reconciliationStatus = "已退款";
        }

        if ("异常".equals(reconciliationStatus)) {
            if (orderId == null || orderId.isBlank()) {
                mismatchReason = "支付单没有对应订单";
            } else if (!payment.equals(order)) {
                mismatchReason = "支付状态与订单状态不一致";
            } else if (!sameMoney(paymentAmount, orderAmount)) {
                mismatchReason = "支付金额与订单金额不一致";
            } else if (eventCount <= 0) {
                mismatchReason = "支付事件缺失";
            } else {
                mismatchReason = "支付链路需要人工核查";
            }
        }

        String user = text(repair(nickname), text(deviceId, "U"));
        AdminReconciliationResponse.Row response = new AdminReconciliationResponse.Row(
                paymentId,
                orderId,
                user,
                money(paymentAmount),
                orderAmount == null ? null : money(orderAmount),
                payment,
                orderId == null || orderId.isBlank() ? "" : order,
                reconciliationStatus,
                mismatchReason,
                text(repair(method), "未知支付方式"),
                repair(providerTradeNo),
                eventCount,
                repair(latestEventType),
                timeText(createdAt),
                timeText(paidAt == null ? latestEventAt : paidAt)
        );
        return new ReconciliationRow(response);
    }

    private AdminReconciliationResponse.Summary summary(List<ReconciliationRow> rows) {
        BigDecimal paidAmount = rows.stream()
                .filter(row -> "PAID".equals(row.response().paymentStatus()))
                .map(row -> row.response().paymentAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount = rows.stream()
                .filter(row -> "REFUNDED".equals(row.response().paymentStatus()))
                .map(row -> row.response().paymentAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AdminReconciliationResponse.Summary(
                rows.size(),
                countStatus(rows, "已对账"),
                countStatus(rows, "待支付"),
                countStatus(rows, "已退款"),
                countStatus(rows, "异常"),
                money(paidAmount),
                money(refundedAmount)
        );
    }

    private int countStatus(List<ReconciliationRow> rows, String status) {
        return (int) rows.stream()
                .filter(row -> status.equals(row.response().reconciliationStatus()))
                .count();
    }

    private boolean matches(ReconciliationRow row, String search) {
        if (search.isBlank()) {
            return true;
        }
        AdminReconciliationResponse.Row value = row.response();
        String haystack = String.join(" ",
                safe(value.paymentId()),
                safe(value.orderId()),
                safe(value.user()),
                safe(value.method()),
                safe(value.providerTradeNo()),
                safe(value.reconciliationStatus()),
                safe(value.mismatchReason())
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(search);
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        return left != null && right != null && money(left).compareTo(money(right)) == 0;
    }

    private String normalizeStatus(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        if (value.equals("PAID") || value.equals("SUCCESS") || value.equals("已支付")) return "PAID";
        if (value.equals("REFUNDED") || value.equals("REFUND") || value.equals("已退款")) return "REFUNDED";
        if (value.equals("PENDING") || value.equals("PROCESSING") || value.equals("处理中")) return "PENDING";
        return value;
    }

    private String roi(BigDecimal revenue, BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.00";
        }
        return revenue.subtract(cost)
                .divide(cost, 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyText(BigDecimal value) {
        return "¥" + money(value).toPlainString();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String timeText(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_TEXT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(String value, String fallback) {
        String clean = safe(value);
        return clean.isBlank() ? fallback : clean;
    }

    private record ReconciliationRow(AdminReconciliationResponse.Row response) {
    }
}
