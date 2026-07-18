package com.shortvideo.backend.admin;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.admin.dto.AdminAuthResponse;
import com.shortvideo.backend.admin.dto.AdminLoginRequest;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.common.PasswordHashService;
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
    private final PasswordHashService passwordHashService;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapDisplayName;
    private final String legacyAdminToken;
    private final int maxLoginFailures;
    private final Duration loginLockout;
    private final ConcurrentMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    public AdminAuthService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            PasswordHashService passwordHashService,
            @Value("${app.security.bootstrap-admin-username}") String bootstrapUsername,
            @Value("${app.security.bootstrap-admin-password}") String bootstrapPassword,
            @Value("${app.security.bootstrap-admin-name}") String bootstrapDisplayName,
            @Value("${app.security.admin-token:}") String legacyAdminToken,
            @Value("${app.security.admin-login-max-failures:5}") int maxLoginFailures,
            @Value("${app.security.admin-login-lockout-minutes:10}") int loginLockoutMinutes
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.passwordHashService = passwordHashService;
        this.bootstrapUsername = clean(bootstrapUsername, "admin");
        this.bootstrapPassword = clean(bootstrapPassword, "Admin@123456");
        this.bootstrapDisplayName = clean(bootstrapDisplayName, "Administrator");
        this.legacyAdminToken = legacyAdminToken == null ? "" : legacyAdminToken.trim();
        this.maxLoginFailures = Math.max(1, maxLoginFailures);
        this.loginLockout = Duration.ofMinutes(Math.max(1, loginLockoutMinutes));
    }

    @PostConstruct
    void ensureBootstrapAdmin() {
        PasswordHashService.EncodedPassword passwordHash = passwordHashService.encode(bootstrapPassword);
        jdbc.update("""
                INSERT INTO admin_users
                (username, password_salt, password_hash, display_name, role_key, permissions, status)
                SELECT ?, ?, ?, ?, 'administrator', CAST(? AS JSON), 'ENABLED'
                FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM admin_users)
                ON DUPLICATE KEY UPDATE username = username
                """,
                bootstrapUsername,
                passwordHash.salt(),
                passwordHash.hash(),
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

        String attemptKey = username.toLowerCase(Locale.ROOT);
        ensureLoginAllowed(attemptKey);
        AdminUserRow row = findAdminByUsername(username);
        if (row == null || !passwordHashService.matches(row.passwordSalt(), row.passwordHash(), password)) {
            recordFailedLogin(attemptKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin account or password is invalid");
        }
        if (!"ENABLED".equalsIgnoreCase(row.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin account is disabled");
        }
        clearLoginAttempt(attemptKey);
        upgradePasswordHashIfNeeded(row, password);

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

    public Optional<AdminProfileResponse> authenticatedProfile(String authorization, String legacyToken) {
        try {
            return Optional.of(current(authorization, legacyToken));
        } catch (ResponseStatusException ex) {
            return Optional.empty();
        }
    }

    public AdminProfileResponse current(String authorization, String legacyToken) {
        AdminProfileResponse legacy = legacyProfile(authorization, legacyToken);
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

    public AdminProfileResponse requirePermission(String authorization, String legacyToken, String permission) {
        AdminProfileResponse profile = current(authorization, legacyToken);
        if (hasPermission(profile, permission)) {
            return profile;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin permission is required: " + permission);
    }

    private boolean hasPermission(AdminProfileResponse profile, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (profile != null && "administrator".equalsIgnoreCase(profile.role())) {
            return true;
        }
        return profile != null
                && profile.permissions() != null
                && profile.permissions().contains(permission);
    }

    private AdminProfileResponse legacyProfile(String authorization, String legacyToken) {
        if (legacyAdminToken.isBlank()) {
            return null;
        }
        String token = legacyToken == null ? "" : legacyToken.trim();
        String authorizationToken = bearerToken(authorization);
        String rawAuthorization = authorization == null ? "" : authorization.trim();
        if (!legacyAdminToken.equals(token)
                && !legacyAdminToken.equals(authorizationToken)
                && !legacyAdminToken.equals(rawAuthorization)) {
            return null;
        }
        return new AdminProfileResponse(0L, "token-admin", "Token Admin", "administrator", DEFAULT_PERMISSIONS);
    }

    private void ensureLoginAllowed(String key) {
        LoginAttempt attempt = loginAttempts.get(key);
        if (attempt == null || attempt.lockedUntil() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (attempt.lockedUntil().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Please try again later");
        }
        loginAttempts.remove(key, attempt);
    }

    private void recordFailedLogin(String key) {
        LocalDateTime now = LocalDateTime.now();
        loginAttempts.compute(key, (ignored, existing) -> {
            int failures = existing == null || isLockExpired(existing, now) ? 1 : existing.failures() + 1;
            LocalDateTime lockedUntil = failures >= maxLoginFailures ? now.plus(loginLockout) : null;
            return new LoginAttempt(failures, lockedUntil);
        });
    }

    private void clearLoginAttempt(String key) {
        loginAttempts.remove(key);
    }

    private boolean isLockExpired(LoginAttempt attempt, LocalDateTime now) {
        return attempt.lockedUntil() != null && !attempt.lockedUntil().isAfter(now);
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
        return new AdminProfileResponse(row.id(), row.username(), row.displayName(), row.roleKey(), resolvedPermissions(row));
    }

    private List<String> resolvedPermissions(AdminUserRow row) {
        List<String> rolePermissions = rolePermissions(row.roleKey());
        if (!rolePermissions.isEmpty()) {
            return rolePermissions;
        }
        return row.permissions() == null ? List.of() : row.permissions();
    }

    private List<String> rolePermissions(String roleKey) {
        String key = clean(roleKey, "");
        if (key.isBlank()) {
            return List.of();
        }
        try {
            return jdbc.query("""
                    SELECT permission_key
                    FROM admin_role_permissions
                    WHERE role_key = ?
                    ORDER BY permission_key
                    """, (rs, rowNum) -> rs.getString("permission_key"), key);
        } catch (Exception ex) {
            return List.of();
        }
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

    private void upgradePasswordHashIfNeeded(AdminUserRow row, String password) {
        if (!passwordHashService.needsUpgrade(row.passwordSalt(), row.passwordHash())) {
            return;
        }
        PasswordHashService.EncodedPassword passwordHash = passwordHashService.encode(password);
        jdbc.update("""
                UPDATE admin_users
                SET password_salt = ?, password_hash = ?
                WHERE id = ?
                """, passwordHash.salt(), passwordHash.hash(), row.id());
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

    private record LoginAttempt(int failures, LocalDateTime lockedUntil) {
    }
}
