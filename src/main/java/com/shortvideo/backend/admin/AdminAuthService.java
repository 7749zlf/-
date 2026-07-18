package com.shortvideo.backend.admin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.shortvideo.backend.admin.dto.AdminAuthResponse;
import com.shortvideo.backend.admin.dto.AdminLoginRequest;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.admin.repository.AdminAuthRepository;
import com.shortvideo.backend.admin.repository.AdminUserRecord;
import com.shortvideo.backend.common.PasswordHashService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    private final AdminAuthRepository authRepository;
    private final PasswordHashService passwordHashService;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapDisplayName;
    private final String legacyAdminToken;
    private final int maxLoginFailures;
    private final Duration loginLockout;
    private final ConcurrentMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    public AdminAuthService(
            PasswordHashService passwordHashService,
            AdminAuthRepository authRepository,
            @Value("${app.security.bootstrap-admin-username}") String bootstrapUsername,
            @Value("${app.security.bootstrap-admin-password}") String bootstrapPassword,
            @Value("${app.security.bootstrap-admin-name}") String bootstrapDisplayName,
            @Value("${app.security.admin-token:}") String legacyAdminToken,
            @Value("${app.security.admin-login-max-failures:5}") int maxLoginFailures,
            @Value("${app.security.admin-login-lockout-minutes:10}") int loginLockoutMinutes
    ) {
        this.passwordHashService = passwordHashService;
        this.authRepository = authRepository;
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
        authRepository.createBootstrapAdminIfMissing(
                bootstrapUsername,
                passwordHash,
                bootstrapDisplayName,
                DEFAULT_PERMISSIONS
        );
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
        AdminUserRecord row = authRepository.findByUsername(username).orElse(null);
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
        authRepository.saveToken(token, row.id(), LocalDateTime.now().plusSeconds(TOKEN_SECONDS));
        return new AdminAuthResponse(token, TOKEN_SECONDS, toProfile(row));
    }

    @Transactional
    public void logout(String authorization) {
        String token = bearerToken(authorization);
        if (!token.isBlank()) {
            authRepository.revokeToken(token);
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

        AdminUserRecord row = authRepository.findActiveAdminByToken(token).orElse(null);

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

    private AdminProfileResponse toProfile(AdminUserRecord row) {
        return new AdminProfileResponse(row.id(), row.username(), row.displayName(), row.roleKey(), resolvedPermissions(row));
    }

    private List<String> resolvedPermissions(AdminUserRecord row) {
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
            return authRepository.findRolePermissions(key);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void upgradePasswordHashIfNeeded(AdminUserRecord row, String password) {
        if (!passwordHashService.needsUpgrade(row.passwordSalt(), row.passwordHash())) {
            return;
        }
        PasswordHashService.EncodedPassword passwordHash = passwordHashService.encode(password);
        authRepository.updatePasswordHash(row.id(), passwordHash.salt(), passwordHash.hash());
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

    private record LoginAttempt(int failures, LocalDateTime lockedUntil) {
    }
}
