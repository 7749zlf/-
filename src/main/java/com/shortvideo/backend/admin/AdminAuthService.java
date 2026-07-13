package com.shortvideo.backend.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.admin.dto.AdminAuthResponse;
import com.shortvideo.backend.admin.dto.AdminLoginRequest;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthService {

    private static final int TOKEN_SECONDS = 7200;
    private static final List<String> DEFAULT_PERMISSIONS = List.of(
            "dashboard",
            "content",
            "storyline",
            "orders",
            "channels",
            "media",
            "finance",
            "users",
            "roles",
            "settings"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapDisplayName;
    private final String legacyAdminToken;

    public AdminAuthService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${app.security.bootstrap-admin-username:admin}") String bootstrapUsername,
            @Value("${app.security.bootstrap-admin-password:Admin@123456}") String bootstrapPassword,
            @Value("${app.security.bootstrap-admin-name:Administrator}") String bootstrapDisplayName,
            @Value("${app.security.admin-token:}") String legacyAdminToken
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.bootstrapUsername = clean(bootstrapUsername, "admin");
        this.bootstrapPassword = clean(bootstrapPassword, "Admin@123456");
        this.bootstrapDisplayName = clean(bootstrapDisplayName, "Administrator");
        this.legacyAdminToken = legacyAdminToken == null ? "" : legacyAdminToken.trim();
    }

    @PostConstruct
    void ensureBootstrapAdmin() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_users", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        String salt = randomToken();
        jdbc.update("""
                INSERT INTO admin_users
                (username, password_salt, password_hash, display_name, role_key, permissions, status)
                VALUES (?, ?, ?, ?, 'administrator', CAST(? AS JSON), 'ENABLED')
                """,
                bootstrapUsername,
                salt,
                hashPassword(salt, bootstrapPassword),
                bootstrapDisplayName,
                writeJson(DEFAULT_PERMISSIONS));
    }

    @Transactional
    public AdminAuthResponse login(AdminLoginRequest request) {
        String username = clean(request == null ? null : request.username(), "");
        String password = request == null ? "" : clean(request.password(), "");
        if (username.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        AdminUserRow row = findAdminByUsername(username);
        if (row == null || !constantTimeEquals(row.passwordHash(), hashPassword(row.passwordSalt(), password))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin account or password is invalid");
        }
        if (!"ENABLED".equalsIgnoreCase(row.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin account is disabled");
        }

        String token = "adm_" + randomToken();
        jdbc.update("""
                INSERT INTO admin_auth_tokens (token, admin_id, expires_at)
                VALUES (?, ?, ?)
                """, token, row.id(), Timestamp.valueOf(LocalDateTime.now().plusSeconds(TOKEN_SECONDS)));
        return new AdminAuthResponse(token, TOKEN_SECONDS, toProfile(row));
    }

    @Transactional
    public void logout(String authorization) {
        String token = bearerToken(authorization);
        if (!token.isBlank()) {
            jdbc.update("UPDATE admin_auth_tokens SET revoked = TRUE WHERE token = ?", token);
        }
    }

    public AdminProfileResponse current(String authorization, String legacyToken) {
        AdminProfileResponse legacy = legacyProfile(legacyToken);
        if (legacy != null) {
            return legacy;
        }

        String token = bearerToken(authorization);
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin login is required");
        }

        AdminUserRow row = jdbc.query("""
                SELECT a.id, a.username, a.password_salt, a.password_hash, a.display_name,
                       a.role_key, JSON_UNQUOTE(JSON_EXTRACT(a.permissions, '$')) AS permissions, a.status
                FROM admin_auth_tokens t
                JOIN admin_users a ON a.id = t.admin_id
                WHERE t.token = ?
                  AND t.revoked = FALSE
                  AND t.expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> new AdminUserRow(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_salt"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("role_key"),
                permissions(rs.getString("permissions")),
                rs.getString("status")
        ), token).stream().findFirst().orElse(null);

        if (row == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid or expired");
        }
        if (!"ENABLED".equalsIgnoreCase(row.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin account is disabled");
        }
        return toProfile(row);
    }

    public AdminProfileResponse requireAdmin(String authorization, String legacyToken) {
        return current(authorization, legacyToken);
    }

    private AdminProfileResponse legacyProfile(String legacyToken) {
        if (legacyAdminToken.isBlank()) {
            return null;
        }
        String token = legacyToken == null ? "" : legacyToken.trim();
        if (!legacyAdminToken.equals(token)) {
            return null;
        }
        return new AdminProfileResponse(0L, "token-admin", "Token Admin", "administrator", DEFAULT_PERMISSIONS);
    }

    private AdminUserRow findAdminByUsername(String username) {
        return jdbc.query("""
                SELECT id, username, password_salt, password_hash, display_name,
                       role_key, JSON_UNQUOTE(JSON_EXTRACT(permissions, '$')) AS permissions, status
                FROM admin_users
                WHERE username = ?
                """, (rs, rowNum) -> new AdminUserRow(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_salt"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("role_key"),
                permissions(rs.getString("permissions")),
                rs.getString("status")
        ), username).stream().findFirst().orElse(null);
    }

    private AdminProfileResponse toProfile(AdminUserRow row) {
        return new AdminProfileResponse(row.id(), row.username(), row.displayName(), row.roleKey(), row.permissions());
    }

    private List<String> permissions(String json) {
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

    private String hashPassword(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8),
                actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return "";
        }
        return authorization.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private String clean(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isBlank() ? fallback : text;
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record AdminUserRow(
            Long id,
            String username,
            String passwordSalt,
            String passwordHash,
            String displayName,
            String roleKey,
            List<String> permissions,
            String status
    ) {
    }
}
