package com.shortvideo.backend.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shortvideo.backend.admin.dto.AdminUserRequest;
import com.shortvideo.backend.admin.dto.AdminUserResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserManagementService {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;

    public AdminUserManagementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminUserResponse> listUsers(String keyword) {
        String like = "%" + safe(keyword).toLowerCase() + "%";
        return jdbc.query("""
                SELECT u.id, u.device_id, u.phone, u.nickname, u.level, u.status,
                       u.email, u.admin_note, u.paid_amount, u.created_at, u.last_active_at,
                       (SELECT COUNT(*) FROM watch_history w WHERE w.user_id = u.id) AS watch_count
                FROM app_users u
                WHERE ? = '%%'
                   OR LOWER(u.nickname) LIKE ?
                   OR LOWER(u.device_id) LIKE ?
                   OR LOWER(COALESCE(u.phone, '')) LIKE ?
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
        ), like, like, like, like);
    }

    @Transactional
    public AdminUserResponse createUser(AdminUserRequest request) {
        String name = text(request == null ? null : request.name(), "追剧用户");
        String level = text(request == null ? null : request.level(), "新用户");
        String status = toInternalStatus(request == null ? null : request.status());
        String email = safe(request == null ? null : request.email());
        String note = safe(request == null ? null : request.note());
        BigDecimal paid = parseMoney(request == null ? null : request.paid());
        String deviceId = text(request == null ? null : request.deviceId(), "admin-created-" + UUID.randomUUID());

        jdbc.update("""
                INSERT INTO app_users
                (device_id, nickname, level, status, email, admin_note, paid_amount, last_active_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, deviceId, name, level, status, email, note, paid);
        Long id = jdbc.queryForObject("SELECT id FROM app_users WHERE device_id = ?", Long.class, deviceId);
        return getUser(id == null ? 0 : id);
    }

    @Transactional
    public AdminUserResponse updateUser(String id, AdminUserRequest request) {
        long userId = numericId(id);
        AdminUserResponse current = getUser(userId);
        jdbc.update("""
                UPDATE app_users
                SET nickname = ?, level = ?, status = ?, email = ?, admin_note = ?, paid_amount = ?
                WHERE id = ?
                """,
                text(request == null ? null : request.name(), current.name()),
                text(request == null ? null : request.level(), current.level()),
                toInternalStatus(request == null ? null : request.status()),
                safe(request == null ? null : request.email()),
                safe(request == null ? null : request.note()),
                parseMoney(request == null ? null : request.paid()),
                userId);
        return getUser(userId);
    }

    @Transactional
    public AdminUserResponse updateStatus(String id, String status) {
        long userId = numericId(id);
        jdbc.update("UPDATE app_users SET status = ? WHERE id = ?", toInternalStatus(status), userId);
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
        return new AdminUserResponse(
                "U" + id,
                id,
                deviceId,
                phone == null ? "" : phone,
                nickname,
                level,
                "¥" + (paid == null ? BigDecimal.ZERO : paid).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                watchCount + " 集",
                toDisplayStatus(status),
                activeAt == null ? "" : activeAt.format(TIME_TEXT),
                email == null ? "" : email,
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
