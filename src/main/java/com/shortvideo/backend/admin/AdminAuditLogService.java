package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.admin.dto.AdminAuditLogResponse;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditLogService {

    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminAuditLogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<AdminAuditLogResponse> listLogs() {
        return jdbc.query("""
                SELECT id, actor_username, action, target_type, target_id,
                       JSON_UNQUOTE(JSON_EXTRACT(details, '$.detail')) AS detail,
                       created_at
                FROM admin_audit_logs
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, (rs, rowNum) -> new AdminAuditLogResponse(
                rs.getLong("id"),
                repair(rs.getString("action")),
                repair(rs.getString("target_type")),
                detail(rs.getString("detail"), rs.getString("target_id")),
                timeText(toLocalDateTime(rs.getTimestamp("created_at"))),
                repair(rs.getString("actor_username"))
        ));
    }

    public void record(AdminProfileResponse actor, String action, String targetType, String targetId, String detail) {
        jdbc.update("""
                INSERT INTO admin_audit_logs (actor_id, actor_username, action, target_type, target_id, details)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON))
                """,
                actor == null ? null : actor.id(),
                actor == null ? "system" : actor.username(),
                safe(action, "后台操作"),
                safe(targetType, "后台"),
                safe(targetId, ""),
                detailsJson(detail)
        );
    }

    private String detailsJson(String detail) {
        try {
            return objectMapper.writeValueAsString(Map.of("detail", safe(detail, "")));
        } catch (Exception ex) {
            return "{\"detail\":\"\"}";
        }
    }

    private String detail(String detail, String targetId) {
        String value = repair(detail);
        if (value == null || value.isBlank()) {
            return targetId == null ? "" : targetId;
        }
        return value;
    }

    private String timeText(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_TEXT);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String safe(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isBlank() ? fallback : text;
    }
}
