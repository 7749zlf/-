package com.shortvideo.backend.admin.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.common.PasswordHashService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAuthRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminAuthRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void createBootstrapAdminIfMissing(
            String username,
            PasswordHashService.EncodedPassword passwordHash,
            String displayName,
            List<String> permissions
    ) {
        jdbc.update("""
                INSERT INTO admin_users
                (username, password_salt, password_hash, display_name, role_key, permissions, status)
                SELECT ?, ?, ?, ?, 'administrator', CAST(? AS JSON), 'ENABLED'
                FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM admin_users)
                ON DUPLICATE KEY UPDATE username = username
                """,
                username,
                passwordHash.salt(),
                passwordHash.hash(),
                displayName,
                writeJson(permissions));
    }

    public Optional<AdminUserRecord> findByUsername(String username) {
        return jdbc.query("""
                SELECT id, username, password_salt, password_hash, display_name,
                       role_key, JSON_UNQUOTE(JSON_EXTRACT(permissions, '$')) AS permissions, status
                FROM admin_users
                WHERE username = ?
                """, (rs, rowNum) -> new AdminUserRecord(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_salt"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("role_key"),
                readPermissions(rs.getString("permissions")),
                rs.getString("status")
        ), username).stream().findFirst();
    }

    public Optional<AdminUserRecord> findActiveAdminByToken(String token) {
        return jdbc.query("""
                SELECT a.id, a.username, a.password_salt, a.password_hash, a.display_name,
                       a.role_key, JSON_UNQUOTE(JSON_EXTRACT(a.permissions, '$')) AS permissions, a.status
                FROM admin_auth_tokens t
                JOIN admin_users a ON a.id = t.admin_id
                WHERE t.token = ?
                  AND t.revoked = FALSE
                  AND t.expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> new AdminUserRecord(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_salt"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("role_key"),
                readPermissions(rs.getString("permissions")),
                rs.getString("status")
        ), token).stream().findFirst();
    }

    public void saveToken(String token, long adminId, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO admin_auth_tokens (token, admin_id, expires_at)
                VALUES (?, ?, ?)
                """, token, adminId, Timestamp.valueOf(expiresAt));
    }

    public void revokeToken(String token) {
        jdbc.update("UPDATE admin_auth_tokens SET revoked = TRUE WHERE token = ?", token);
    }

    public List<String> findRolePermissions(String roleKey) {
        return jdbc.query("""
                SELECT permission_key
                FROM admin_role_permissions
                WHERE role_key = ?
                ORDER BY permission_key
                """, (rs, rowNum) -> rs.getString("permission_key"), roleKey);
    }

    public void updatePasswordHash(long adminId, String salt, String hash) {
        jdbc.update("""
                UPDATE admin_users
                SET password_salt = ?, password_hash = ?
                WHERE id = ?
                """, salt, hash, adminId);
    }

    private List<String> readPermissions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String writeJson(List<String> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
