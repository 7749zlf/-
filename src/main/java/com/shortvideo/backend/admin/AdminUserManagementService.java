package com.shortvideo.backend.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shortvideo.backend.admin.dto.AdminUserRequest;
import com.shortvideo.backend.admin.dto.AdminUserDetailResponse;
import com.shortvideo.backend.admin.dto.AdminUserResponse;
import com.shortvideo.backend.common.DataProtectionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserManagementService {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final DataProtectionService dataProtection;
    private final AdminAuditService auditService;

    public AdminUserManagementService(
            JdbcTemplate jdbc,
            DataProtectionService dataProtection,
            AdminAuditService auditService
    ) {
        this.jdbc = jdbc;
        this.dataProtection = dataProtection;
        this.auditService = auditService;
    }

    public List<AdminUserResponse> listUsers(String keyword) {
        String keywordText = safe(keyword);
        String like = "%" + keywordText.toLowerCase(Locale.ROOT) + "%";
        String phoneHash = dataProtection.phoneFingerprint(keywordText);
        String emailHash = dataProtection.emailFingerprint(keywordText);
        return jdbc.query("""
                SELECT u.id, u.device_id, u.phone, u.nickname, u.level, u.status,
                       u.email, u.admin_note, u.paid_amount, u.created_at, u.last_active_at,
                       (SELECT COUNT(*) FROM watch_history w WHERE w.user_id = u.id) AS watch_count
                FROM app_users u
                WHERE ? = ''
                   OR LOWER(u.nickname) LIKE ?
                   OR LOWER(u.device_id) LIKE ?
                   OR COALESCE(u.phone_hash, '') = ?
                   OR COALESCE(u.email_hash, '') = ?
                ORDER BY COALESCE(u.last_active_at, u.created_at) DESC, u.id DESC
                LIMIT 200
                """, (rs, rowNum) -> toResponse(
                rs.getLong("id"),
                rs.getString("device_id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getString("level"),
                rs.getString("status"),
                rs.getString("email"),
                rs.getString("admin_note"),
                rs.getBigDecimal("paid_amount"),
                rs.getInt("watch_count"),
                toLocalDateTime(rs.getTimestamp("last_active_at")),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), keywordText, like, like, phoneHash, emailHash);
    }

    public AdminUserDetailResponse getUserDetail(String id) {
        long userId = numericId(id);
        AdminUserResponse user = getUser(userId);
        AdminUserDetailResponse.Wallet wallet = jdbc.query("""
                SELECT balance, paid_amount,
                       (SELECT COUNT(*) FROM recharge_records WHERE user_id = u.id) AS recharge_count,
                       (SELECT COUNT(*) FROM orders WHERE user_id = u.id) AS order_count,
                       (SELECT COUNT(*) FROM user_unlocks WHERE user_id = u.id) AS unlock_count,
                       (SELECT COUNT(*) FROM watch_history WHERE user_id = u.id) AS watch_count,
                       (SELECT COUNT(*) FROM user_follows WHERE user_id = u.id) AS follow_count,
                       (SELECT COUNT(*) FROM episode_likes WHERE user_id = u.id) AS like_count
                FROM app_users u
                WHERE u.id = ?
                """, (rs, rowNum) -> {
            BigDecimal balance = money(rs.getBigDecimal("balance"));
            BigDecimal paidAmount = money(rs.getBigDecimal("paid_amount"));
            return new AdminUserDetailResponse.Wallet(
                    balance,
                    moneyText(balance),
                    paidAmount,
                    moneyText(paidAmount),
                    rs.getInt("recharge_count"),
                    rs.getInt("order_count"),
                    rs.getInt("unlock_count"),
                    rs.getInt("watch_count"),
                    rs.getInt("follow_count"),
                    rs.getInt("like_count")
            );
        }, userId).stream().findFirst().orElse(new AdminUserDetailResponse.Wallet(
                BigDecimal.ZERO, moneyText(BigDecimal.ZERO), BigDecimal.ZERO, moneyText(BigDecimal.ZERO), 0, 0, 0, 0, 0, 0
        ));

        return new AdminUserDetailResponse(
                user,
                wallet,
                listRechargeItems(userId),
                listOrderItems(userId),
                listUnlockItems(userId),
                listWatchItems(userId),
                listFollowItems(userId),
                listLikeItems(userId),
                listPaymentItems(userId),
                listPaymentEventItems(userId)
        );
    }

    private List<AdminUserDetailResponse.RechargeItem> listRechargeItems(long userId) {
        return jdbc.query("""
                SELECT id, amount, method_key, method_name, status, created_at
                FROM recharge_records
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """, (rs, rowNum) -> {
            BigDecimal amount = money(rs.getBigDecimal("amount"));
            return new AdminUserDetailResponse.RechargeItem(
                    rs.getString("id"),
                    amount,
                    moneyText(amount),
                    rs.getString("method_key"),
                    rs.getString("method_name"),
                    rs.getString("status"),
                    timeText(toLocalDateTime(rs.getTimestamp("created_at")))
            );
        }, userId);
    }

    private List<AdminUserDetailResponse.OrderItem> listOrderItems(long userId) {
        return jdbc.query("""
                SELECT id, title, amount, payment_method, status, paid_at, created_at
                FROM orders
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """, (rs, rowNum) -> {
            BigDecimal amount = money(rs.getBigDecimal("amount"));
            return new AdminUserDetailResponse.OrderItem(
                    rs.getString("id"),
                    rs.getString("title"),
                    amount,
                    moneyText(amount),
                    rs.getString("payment_method"),
                    rs.getString("status"),
                    timeText(toLocalDateTime(rs.getTimestamp("paid_at"))),
                    timeText(toLocalDateTime(rs.getTimestamp("created_at")))
            );
        }, userId);
    }

    private List<AdminUserDetailResponse.UnlockItem> listUnlockItems(long userId) {
        return jdbc.query("""
                SELECT u.storyline_id, s.name AS storyline_name, d.title AS drama_title, u.order_id, u.created_at
                FROM user_unlocks u
                JOIN storylines s ON s.id = u.storyline_id
                JOIN dramas d ON d.id = u.drama_id
                WHERE u.user_id = ?
                ORDER BY u.created_at DESC
                LIMIT 20
                """, (rs, rowNum) -> new AdminUserDetailResponse.UnlockItem(
                rs.getString("storyline_id"),
                rs.getString("storyline_name"),
                rs.getString("drama_title"),
                rs.getString("order_id"),
                timeText(toLocalDateTime(rs.getTimestamp("created_at")))
        ), userId);
    }

    private List<AdminUserDetailResponse.WatchItem> listWatchItems(long userId) {
        return jdbc.query("""
                SELECT w.id, d.title AS drama_title, e.title AS episode_title,
                       s.name AS storyline_name, w.episode_number, w.progress_seconds,
                       w.duration_seconds, w.updated_at
                FROM watch_history w
                JOIN dramas d ON d.id = w.drama_id
                JOIN episodes e ON e.id = w.episode_id
                LEFT JOIN storylines s ON s.id = w.storyline_id
                WHERE w.user_id = ?
                ORDER BY w.updated_at DESC
                LIMIT 20
                """, (rs, rowNum) -> new AdminUserDetailResponse.WatchItem(
                String.valueOf(rs.getLong("id")),
                rs.getString("drama_title"),
                rs.getString("episode_title"),
                rs.getString("storyline_name"),
                rs.getInt("episode_number"),
                rs.getInt("progress_seconds"),
                rs.getInt("duration_seconds"),
                timeText(toLocalDateTime(rs.getTimestamp("updated_at")))
        ), userId);
    }

    private List<AdminUserDetailResponse.DramaItem> listFollowItems(long userId) {
        return jdbc.query("""
                SELECT d.id, d.title, d.tag, f.created_at
                FROM user_follows f
                JOIN dramas d ON d.id = f.drama_id
                WHERE f.user_id = ?
                ORDER BY f.created_at DESC
                LIMIT 20
                """, (rs, rowNum) -> new AdminUserDetailResponse.DramaItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("tag"),
                timeText(toLocalDateTime(rs.getTimestamp("created_at")))
        ), userId);
    }

    private List<AdminUserDetailResponse.LikeItem> listLikeItems(long userId) {
        return jdbc.query("""
                SELECT e.id, d.title AS drama_title, e.title AS episode_title,
                       s.name AS storyline_name, l.created_at
                FROM episode_likes l
                JOIN episodes e ON e.id = l.episode_id
                JOIN dramas d ON d.id = e.drama_id
                LEFT JOIN storylines s ON s.id = e.storyline_id
                WHERE l.user_id = ?
                ORDER BY l.created_at DESC
                LIMIT 20
                """, (rs, rowNum) -> new AdminUserDetailResponse.LikeItem(
                rs.getString("id"),
                rs.getString("drama_title"),
                rs.getString("episode_title"),
                rs.getString("storyline_name"),
                timeText(toLocalDateTime(rs.getTimestamp("created_at")))
        ), userId);
    }

    private List<AdminUserDetailResponse.PaymentItem> listPaymentItems(long userId) {
        return jdbc.query("""
                SELECT id, amount, method_name, status, provider_trade_no, created_at, paid_at
                FROM h5_payments
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """, (rs, rowNum) -> {
            BigDecimal amount = money(rs.getBigDecimal("amount"));
            return new AdminUserDetailResponse.PaymentItem(
                    rs.getString("id"),
                    amount,
                    moneyText(amount),
                    rs.getString("method_name"),
                    rs.getString("status"),
                    rs.getString("provider_trade_no"),
                    timeText(toLocalDateTime(rs.getTimestamp("created_at"))),
                    timeText(toLocalDateTime(rs.getTimestamp("paid_at")))
            );
        }, userId);
    }

    private List<AdminUserDetailResponse.PaymentEventItem> listPaymentEventItems(long userId) {
        return jdbc.query("""
                SELECT e.id, e.payment_id, e.event_type, e.status, e.provider_trade_no, e.message, e.created_at
                FROM h5_payment_events e
                JOIN h5_payments p ON p.id = e.payment_id
                WHERE p.user_id = ?
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT 40
                """, (rs, rowNum) -> new AdminUserDetailResponse.PaymentEventItem(
                rs.getLong("id"),
                rs.getString("payment_id"),
                rs.getString("event_type"),
                rs.getString("status"),
                rs.getString("provider_trade_no"),
                rs.getString("message"),
                timeText(toLocalDateTime(rs.getTimestamp("created_at")))
        ), userId);
    }

    @Transactional
    public AdminUserResponse createUser(AdminUserRequest request) {
        String name = text(request == null ? null : request.name(), "追剧用户");
        String level = text(request == null ? null : request.level(), "新用户");
        String status = toInternalStatus(request == null ? null : request.status());
        String email = safe(request == null ? null : request.email());
        String protectedEmail = dataProtection.encryptOrEmpty(email);
        String emailHash = nullableHash(dataProtection.emailFingerprint(email));
        String note = safe(request == null ? null : request.note());
        String deviceId = text(request == null ? null : request.deviceId(), "admin-created-" + UUID.randomUUID());

        jdbc.update("""
                INSERT INTO app_users
                (device_id, nickname, level, status, email, email_hash, admin_note, paid_amount, last_active_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, deviceId, name, level, status, protectedEmail, emailHash, note, BigDecimal.ZERO);
        Long id = jdbc.queryForObject("SELECT id FROM app_users WHERE device_id = ?", Long.class, deviceId);
        auditService.record("USER_CREATE", "user", String.valueOf(id == null ? 0 : id), Map.of(
                "status", status,
                "deviceId", deviceId
        ));
        return getUser(id == null ? 0 : id);
    }

    @Transactional
    public AdminUserResponse updateUser(String id, AdminUserRequest request) {
        long userId = numericId(id);
        AdminUserResponse current = getUser(userId);
        String email = safe(request == null ? null : request.email());
        String protectedEmail = dataProtection.encryptOrEmpty(email);
        String emailHash = nullableHash(dataProtection.emailFingerprint(email));
        String status = request == null || request.status() == null
                ? toInternalStatus(current.status())
                : toInternalStatus(request.status());
        jdbc.update("""
                UPDATE app_users
                SET nickname = ?, level = ?, status = ?, email = ?, email_hash = ?, admin_note = ?
                WHERE id = ?
                """,
                text(request == null ? null : request.name(), current.name()),
                text(request == null ? null : request.level(), current.level()),
                status,
                protectedEmail,
                emailHash,
                safe(request == null ? null : request.note()),
                userId);
        auditService.record("USER_UPDATE", "user", String.valueOf(userId), Map.of(
                "status", status
        ));
        return getUser(userId);
    }

    @Transactional
    public AdminUserResponse updateStatus(String id, String status) {
        long userId = numericId(id);
        String internalStatus = toInternalStatus(status);
        jdbc.update("UPDATE app_users SET status = ? WHERE id = ?", internalStatus, userId);
        auditService.record("USER_STATUS_UPDATE", "user", String.valueOf(userId), Map.of(
                "status", internalStatus
        ));
        return getUser(userId);
    }

    private AdminUserResponse getUser(long userId) {
        return jdbc.query("""
                SELECT u.id, u.device_id, u.phone, u.nickname, u.level, u.status,
                       u.email, u.admin_note, u.paid_amount, u.created_at, u.last_active_at,
                       (SELECT COUNT(*) FROM watch_history w WHERE w.user_id = u.id) AS watch_count
                FROM app_users u
                WHERE u.id = ?
                """, (rs, rowNum) -> toResponse(
                rs.getLong("id"),
                rs.getString("device_id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getString("level"),
                rs.getString("status"),
                rs.getString("email"),
                rs.getString("admin_note"),
                rs.getBigDecimal("paid_amount"),
                rs.getInt("watch_count"),
                toLocalDateTime(rs.getTimestamp("last_active_at")),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), userId).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private AdminUserResponse toResponse(
            long id,
            String deviceId,
            String phone,
            String nickname,
            String level,
            String status,
            String email,
            String note,
            BigDecimal paid,
            int watchCount,
            LocalDateTime lastActiveAt,
            LocalDateTime createdAt
    ) {
        LocalDateTime activeAt = lastActiveAt == null ? createdAt : lastActiveAt;
        String plainPhone = dataProtection.decrypt(phone);
        String plainEmail = dataProtection.decrypt(email);
        return new AdminUserResponse(
                "U" + id,
                id,
                deviceId,
                maskPhone(plainPhone),
                nickname,
                level,
                "¥" + (paid == null ? BigDecimal.ZERO : paid).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                watchCount + " 集",
                toDisplayStatus(status),
                activeAt == null ? "" : activeAt.format(TIME_TEXT),
                plainEmail == null ? "" : plainEmail,
                note == null ? "" : note
        );
    }

    private String toInternalStatus(String status) {
        String text = safe(status);
        if (text.equalsIgnoreCase("FROZEN") || text.equals("冻结")) {
            return "FROZEN";
        }
        if (text.equalsIgnoreCase("WATCH") || text.equals("观察")) {
            return "WATCH";
        }
        return "NORMAL";
    }

    private String toDisplayStatus(String status) {
        if ("FROZEN".equalsIgnoreCase(status)) {
            return "冻结";
        }
        if ("WATCH".equalsIgnoreCase(status)) {
            return "观察";
        }
        return "正常";
    }

    private BigDecimal parseMoney(String value) {
        String cleaned = safe(value).replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyText(BigDecimal value) {
        return "¥" + money(value).toPlainString();
    }

    private String timeText(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_TEXT);
    }

    private long numericId(String value) {
        Matcher matcher = DIGITS.matcher(safe(value));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid user id: " + value);
        }
        return Long.parseLong(matcher.group());
    }

    private String text(String value, String fallback) {
        String safe = safe(value);
        return safe.isBlank() ? fallback : safe;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullableHash(String hash) {
        return hash == null || hash.isBlank() ? null : hash;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
