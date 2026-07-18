package com.shortvideo.backend.h5;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.common.DataProtectionService;
import com.shortvideo.backend.common.PasswordHashService;
import com.shortvideo.backend.h5.dto.ApiOkResponse;
import com.shortvideo.backend.h5.dto.BindPhoneResponse;
import com.shortvideo.backend.h5.dto.ChangePasswordRequest;
import com.shortvideo.backend.h5.dto.FollowedDramaResponse;
import com.shortvideo.backend.h5.dto.GuestLoginRequest;
import com.shortvideo.backend.h5.dto.H5AuthResponse;
import com.shortvideo.backend.h5.dto.H5PreferencesRequest;
import com.shortvideo.backend.h5.dto.H5PreferencesResponse;
import com.shortvideo.backend.h5.dto.H5ProfileResponse;
import com.shortvideo.backend.h5.dto.H5ProfileSummaryResponse;
import com.shortvideo.backend.h5.dto.H5RefundRequest;
import com.shortvideo.backend.h5.dto.H5RefundRequestResponse;
import com.shortvideo.backend.h5.dto.InteractionStateResponse;
import com.shortvideo.backend.h5.dto.LikedEpisodeResponse;
import com.shortvideo.backend.h5.dto.OauthLoginRequest;
import com.shortvideo.backend.h5.dto.PasswordLoginRequest;
import com.shortvideo.backend.h5.dto.PaymentCallbackRequest;
import com.shortvideo.backend.h5.dto.PaymentRequest;
import com.shortvideo.backend.h5.dto.PaymentResponse;
import com.shortvideo.backend.h5.dto.PaymentSettlementResponse;
import com.shortvideo.backend.h5.dto.PhoneLoginRequest;
import com.shortvideo.backend.h5.dto.PlayEventRequest;
import com.shortvideo.backend.h5.dto.RechargeRequest;
import com.shortvideo.backend.h5.dto.RechargeResponse;
import com.shortvideo.backend.h5.dto.RefreshTokenRequest;
import com.shortvideo.backend.h5.dto.RefreshTokenResponse;
import com.shortvideo.backend.h5.dto.RegisterRequest;
import com.shortvideo.backend.h5.dto.SmsCodeRequest;
import com.shortvideo.backend.h5.dto.SmsCodeResponse;
import com.shortvideo.backend.h5.dto.StorylineResponse;
import com.shortvideo.backend.h5.dto.UnlockOrderResponse;
import com.shortvideo.backend.h5.dto.UpdateProfileRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureResponse;
import com.shortvideo.backend.h5.dto.WalletResponse;
import com.shortvideo.backend.h5.dto.WatchHistoryRequest;
import com.shortvideo.backend.h5.dto.WatchHistoryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

@Service
public class H5UserService {

    private static final String DEFAULT_DEVICE_ID = "demo-device";
    private static final String DEFAULT_AVATAR = "/images/default-avatar.svg";
    private static final int TOKEN_SECONDS = 7200;
    private static final int SMS_MIN_INTERVAL_SECONDS = 60;
    private static final int SMS_DAILY_LIMIT = 10;
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter REFUND_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DataProtectionService dataProtection;
    private final PasswordHashService passwordHashService;
    private final String publicBaseUrl;
    private final String avatarUploadDir;
    private final boolean demoRechargeEnabled;
    private final boolean demoPaymentEnabled;
    private final boolean productionMode;
    private final ConcurrentMap<String, SmsRateLimit> smsRateLimits = new ConcurrentHashMap<>();

    public H5UserService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            DataProtectionService dataProtection,
            PasswordHashService passwordHashService,
            @Value("${app.public-base-url}") String publicBaseUrl,
            @Value("${app.storage.avatar-upload-dir}") String avatarUploadDir,
            @Value("${app.demo.recharge-enabled:true}") boolean demoRechargeEnabled,
            @Value("${app.demo.payment-enabled:true}") boolean demoPaymentEnabled,
            @Value("${app.security.production-mode:false}") boolean productionMode
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.dataProtection = dataProtection;
        this.passwordHashService = passwordHashService;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.avatarUploadDir = avatarUploadDir;
        this.demoRechargeEnabled = demoRechargeEnabled;
        this.demoPaymentEnabled = demoPaymentEnabled;
        this.productionMode = productionMode;
    }

    public SmsCodeResponse sendCode(SmsCodeRequest request) {
        if (productionMode) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider is not configured");
        }
        String phone = normalizePhone(request == null ? null : request.phone());
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        String scene = normalizeSmsScene(request == null ? null : request.scene());
        String phoneHash = dataProtection.phoneFingerprint(phone);
        enforceSmsRateLimit(phoneHash, scene);
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        String requestId = "sms-" + shortToken();
        jdbc.update("""
                UPDATE h5_sms_codes
                SET consumed = TRUE, used_at = CURRENT_TIMESTAMP
                WHERE phone_hash = ? AND scene = ? AND consumed = FALSE
                """, phoneHash, scene);
        jdbc.update("""
                INSERT INTO h5_sms_codes
                (phone_hash, scene, code_hash, request_id, expires_at)
                VALUES (?, ?, ?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE))
                """, phoneHash, scene, hashSmsCode(phoneHash, code), requestId);
        return new SmsCodeResponse(true, true, 60, 60, requestId, productionMode ? null : code);
    }

    @Transactional
    public H5AuthResponse guestLogin(GuestLoginRequest request) {
        long userId = findOrCreateUser(request == null ? null : request.deviceId());
        return authResponse(userId);
    }

    @Transactional
    public H5AuthResponse phoneLogin(PhoneLoginRequest request) {
        String phone = normalizePhone(request == null ? null : request.phone());
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        validateSmsCode(phone, "login", request == null ? null : request.code(), false);
        String phoneHash = dataProtection.phoneFingerprint(phone);
        long userId = findUserIdByPhoneHash(phoneHash)
                .orElseGet(() -> findOrCreateUser(request == null ? null : request.deviceId()));
        requireActiveUser(userId);
        phone = dataProtection.encryptOrNull(phone);
        jdbc.update("UPDATE app_users SET phone_hash = ? WHERE id = ?", phoneHash, userId);
        jdbc.update("""
                UPDATE app_users
                SET phone = ?, phone_bound = TRUE, nickname = IF(nickname LIKE '游客 %' OR nickname LIKE 'Guest %', ?, nickname)
                WHERE id = ?
                """, phone, "追剧用户", userId);
        return authResponse(userId);
    }

    @Transactional
    public H5AuthResponse passwordLogin(PasswordLoginRequest request) {
        String account = normalizePhone(request == null ? null : request.account());
        if (account.isBlank()) {
            throw new IllegalArgumentException("account is required");
        }
        String password = safe(request == null ? null : request.password());
        if (password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        String accountHash = dataProtection.phoneFingerprint(account);
        UserPasswordRow user = userPasswordByPhoneHash(accountHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account or password is invalid"));
        if (!passwordMatches(user.passwordSalt(), user.passwordHash(), password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account or password is invalid");
        }
        requireActiveUser(user.userId());
        upgradePasswordHashIfNeeded(user, password);
        return authResponse(user.userId());
    }

    @Transactional
    public H5AuthResponse register(RegisterRequest request) {
        String phone = normalizePhone(request == null ? null : request.phone());
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        validateSmsCode(phone, "register", request == null ? null : request.code(), true);
        String password = safe(request == null ? null : request.password());
        if (password.length() < 6) {
            throw new IllegalArgumentException("password must be at least 6 characters");
        }
        String phoneHash = dataProtection.phoneFingerprint(phone);
        phone = dataProtection.encryptOrNull(phone);
        long userId = findUserIdByPhoneHash(phoneHash)
                .orElseGet(() -> findOrCreateUser(request == null ? null : request.deviceId()));
        PasswordHashService.EncodedPassword passwordHash = passwordHash(password);
        jdbc.update("UPDATE app_users SET phone_hash = ? WHERE id = ?", phoneHash, userId);
        jdbc.update("""
                UPDATE app_users
                SET phone = ?, phone_bound = TRUE, nickname = ?, password_salt = ?, password_hash = ?
                WHERE id = ?
                """, phone, defaultText(request == null ? null : request.nickname(), "追剧用户"),
                passwordHash.salt(), passwordHash.hash(), userId);
        return authResponse(userId);
    }

    @Transactional
    public H5AuthResponse oauthLogin(OauthLoginRequest request) {
        if (productionMode) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "OAuth provider verification is not configured");
        }
        long userId = findOrCreateUser(request == null ? null : request.deviceId());
        String provider = defaultText(request == null ? null : request.provider(), "oauth");
        jdbc.update("""
                UPDATE app_users
                SET nickname = IF(nickname LIKE '游客 %' OR nickname LIKE 'Guest %', ?, nickname)
                WHERE id = ?
                """, provider.toUpperCase(Locale.ROOT) + " 用户", userId);
        return authResponse(userId);
    }

    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request == null ? "" : defaultText(request.refreshToken(), "");
        Long userId = jdbc.query("""
                SELECT user_id
                FROM h5_auth_tokens
                WHERE refresh_token = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong("user_id"), refreshToken).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("refreshToken is invalid"));
        jdbc.update("UPDATE h5_auth_tokens SET revoked = TRUE WHERE refresh_token = ?", refreshToken);
        TokenPair token = issueToken(userId);
        return new RefreshTokenResponse(token.token(), token.refreshToken(), TOKEN_SECONDS);
    }

    @Transactional
    public ApiOkResponse logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            jdbc.update("UPDATE h5_auth_tokens SET revoked = TRUE WHERE refresh_token = ?", refreshToken);
        }
        return new ApiOkResponse(true);
    }

    @Transactional
    public H5ProfileResponse currentUser(String authorization, String deviceId) {
        if (hasBearerToken(authorization)) {
            long userId = userIdFromAuthorization(authorization)
                    .map(this::requireActiveUser)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "H5 login is required"));
            return profileByUserId(userId);
        }
        return profileByDevice(deviceId);
    }

    @Transactional
    public H5ProfileResponse profileByDevice(String deviceId) {
        return profileByUserId(findOrCreateUser(deviceId));
    }

    @Transactional
    public H5ProfileResponse updateProfile(UpdateProfileRequest request, String authorization) {
        long userId = requireActiveUser(requestUserId(authorization, request == null ? null : request.deviceId()));
        jdbc.update("""
                UPDATE app_users
                SET nickname = COALESCE(NULLIF(?, ''), nickname),
                    avatar = COALESCE(NULLIF(?, ''), avatar),
                    gender = COALESCE(NULLIF(?, ''), gender),
                    birthday = COALESCE(?, birthday),
                    bio = COALESCE(NULLIF(?, ''), bio)
                WHERE id = ?
                """,
                safe(request == null ? null : request.nickname()),
                safe(request == null ? null : request.avatar()),
                safe(request == null ? null : request.gender()),
                parseDate(request == null ? null : request.birthday()),
                safe(request == null ? null : request.bio()),
                userId);
        return profileByUserId(userId);
    }

    @Transactional
    public BindPhoneResponse bindPhone(String authorization, String deviceId, String phone, String code) {
        long userId = requireActiveUser(requestUserId(authorization, deviceId));
        String safePhone = normalizePhone(phone);
        if (safePhone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        validateSmsCode(safePhone, "bind-phone", code, true);
        String phoneHash = dataProtection.phoneFingerprint(safePhone);
        findUserIdByPhoneHash(phoneHash)
                .filter(existingUserId -> existingUserId != userId)
                .ifPresent(existingUserId -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone is already bound");
                });
        jdbc.update("UPDATE app_users SET phone = ?, phone_hash = ?, phone_bound = TRUE WHERE id = ?",
                dataProtection.encryptOrNull(safePhone),
                phoneHash,
                userId);
        return new BindPhoneResponse(true, maskPhone(safePhone));
    }

    @Transactional
    public ApiOkResponse changePassword(ChangePasswordRequest request, String authorization) {
        long userId = requireActiveUser(requestUserId(authorization, request == null ? null : request.deviceId()));
        String newPassword = safe(request == null ? null : request.newPassword());
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("newPassword must be at least 6 characters");
        }

        UserPasswordRow current = userPasswordByUserId(userId)
                .orElse(new UserPasswordRow(userId, "", ""));
        if (!current.passwordHash().isBlank()
                && !passwordMatches(current.passwordSalt(), current.passwordHash(), safe(request == null ? null : request.oldPassword()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Old password is invalid");
        }

        PasswordHashService.EncodedPassword nextPassword = passwordHash(newPassword);
        jdbc.update("UPDATE app_users SET password_salt = ?, password_hash = ? WHERE id = ?",
                nextPassword.salt(), nextPassword.hash(), userId);
        return new ApiOkResponse(true);
    }

    public UploadSignatureResponse avatarUploadSignature(UploadSignatureRequest request) {
        if (productionMode) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Upload signing is not configured");
        }
        String fileName = defaultText(request == null ? null : request.fileName(), "avatar.png").replace("\\", "/");
        String key = "avatars/" + shortToken() + "-" + fileName.substring(fileName.lastIndexOf('/') + 1);
        return new UploadSignatureResponse(
                publicUrl("/demo-upload/" + key),
                publicUrl("/static/" + key),
                600
        );
    }

    public UploadSignatureResponse uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件不能为空");
        }
        if (file.getSize() > 5 * 1024 * 1024L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像图片不能超过 5MB");
        }

        String contentType = defaultText(file.getContentType(), "");
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择图片文件");
        }

        Path uploadDir = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
        String fileName = shortToken() + extensionFrom(file.getOriginalFilename(), contentType);
        Path target = uploadDir.resolve(fileName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件名不合法");
        }

        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像上传失败", ex);
        }

        return new UploadSignatureResponse(null, publicUrl("/static/avatars/" + fileName), null);
    }

    @Transactional
    public WalletResponse wallet(String authorization, String deviceId) {
        long userId = requestUserId(authorization, deviceId);
        UserRow user = userRow(userId);
        return new WalletResponse(
                user.deviceId(),
                user.balance(),
                paidAmount(userId),
                count("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM recharge_records WHERE user_id = ?", userId)
        );
    }

    @Transactional
    public WalletResponse wallet(String deviceId) {
        return wallet(null, deviceId);
    }

    @Transactional
    public H5ProfileSummaryResponse profileSummary(String authorization, String deviceId) {
        long userId = requestUserId(authorization, deviceId);
        UserRow user = userRow(userId);
        return new H5ProfileSummaryResponse(
                user.balance(),
                paidAmount(userId),
                count("SELECT COUNT(*) FROM user_follows WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM episode_likes WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM watch_history WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM user_unlocks WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM recharge_records WHERE user_id = ?", userId)
        );
    }

    @Transactional
    public H5PreferencesResponse preferences(String authorization, String deviceId) {
        long userId = requestUserId(authorization, deviceId);
        ensurePreferences(userId);
        return preferencesByUserId(userId);
    }

    @Transactional
    public H5PreferencesResponse preferences(String deviceId) {
        return preferences(null, deviceId);
    }

    @Transactional
    public H5PreferencesResponse updatePreferences(H5PreferencesRequest request, String authorization) {
        long userId = requestUserId(authorization, request == null ? null : request.deviceId());
        ensurePreferences(userId);
        H5PreferencesResponse current = preferencesByUserId(userId);
        jdbc.update("""
                UPDATE user_preferences
                SET auto_play_next = ?, unlock_reminder = ?, muted = ?
                WHERE user_id = ?
                """,
                request == null || request.autoPlayNext() == null ? current.autoPlayNext() : request.autoPlayNext(),
                request == null || request.unlockReminder() == null ? current.unlockReminder() : request.unlockReminder(),
                request == null || request.muted() == null ? current.muted() : request.muted(),
                userId);
        return preferencesByUserId(userId);
    }

    @Transactional
    public WatchHistoryResponse saveWatchHistory(WatchHistoryRequest request, String authorization) {
        if (request == null || request.episodeId() == null || request.episodeId().isBlank()) {
            throw new IllegalArgumentException("episodeId is required");
        }
        long userId = requestUserId(authorization, request.deviceId());
        EpisodeInfo episode = episodeInfo(request.episodeId());
        long dramaId = request.dramaId() == null ? episode.dramaId() : request.dramaId();
        String storylineId = defaultText(request.storylineId(), episode.storylineId());
        if (storylineId != null && storylineId.isBlank()) {
            storylineId = null;
        }

        jdbc.update("""
                INSERT INTO watch_history
                (user_id, drama_id, storyline_id, episode_id, episode_number, progress_seconds, duration_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  drama_id = VALUES(drama_id),
                  storyline_id = VALUES(storyline_id),
                  episode_number = VALUES(episode_number),
                  progress_seconds = VALUES(progress_seconds),
                  duration_seconds = VALUES(duration_seconds),
                  updated_at = CURRENT_TIMESTAMP
                """,
                userId,
                dramaId,
                storylineId,
                request.episodeId(),
                request.episodeNumber() == null ? episode.number() : request.episodeNumber(),
                nonNegative(request.progressSeconds()),
                nonNegative(request.durationSeconds()));

        return listWatchHistoryByUserId(userId).stream()
                .filter(item -> item.episodeId().equals(request.episodeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("watch history save failed"));
    }

    @Transactional
    public List<WatchHistoryResponse> listWatchHistory(String authorization, String deviceId) {
        long userId = requestUserId(authorization, deviceId);
        return listWatchHistoryByUserId(userId);
    }

    @Transactional
    public List<WatchHistoryResponse> listWatchHistory(String deviceId) {
        long userId = findOrCreateUser(deviceId);
        return listWatchHistoryByUserId(userId);
    }

    private List<WatchHistoryResponse> listWatchHistoryByUserId(long userId) {
        return jdbc.query("""
                SELECT w.id, w.drama_id, d.title AS drama_title, w.storyline_id,
                       s.name AS storyline_name, w.episode_id, w.episode_number,
                       e.title AS episode_title, e.cover_url, w.progress_seconds,
                       w.duration_seconds, w.updated_at
                FROM watch_history w
                JOIN dramas d ON d.id = w.drama_id
                JOIN episodes e ON e.id = w.episode_id
                LEFT JOIN storylines s ON s.id = w.storyline_id
                WHERE w.user_id = ?
                ORDER BY w.updated_at DESC
                """, (rs, rowNum) -> new WatchHistoryResponse(
                rs.getLong("id"),
                rs.getLong("drama_id"),
                repair(rs.getString("drama_title")),
                rs.getString("storyline_id"),
                repair(rs.getString("storyline_name")),
                rs.getString("episode_id"),
                rs.getInt("episode_number"),
                repair(rs.getString("episode_title")),
                rs.getString("cover_url"),
                rs.getInt("progress_seconds"),
                rs.getInt("duration_seconds"),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        ), userId);
    }

    @Transactional
    public ApiOkResponse clearWatchHistory(String authorization, String deviceId) {
        jdbc.update("DELETE FROM watch_history WHERE user_id = ?", requireActiveUser(requestUserId(authorization, deviceId)));
        return new ApiOkResponse(true);
    }

    @Transactional
    public ApiOkResponse clearWatchHistory(String deviceId) {
        return clearWatchHistory(null, deviceId);
    }

    @Transactional
    public RechargeResponse recharge(RechargeRequest request, String authorization) {
        if (!demoRechargeEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo recharge is disabled");
        }

        long userId = requestUserId(authorization, request == null ? null : request.deviceId());
        BigDecimal amount = positiveAmount(request == null ? null : request.amount(), new BigDecimal("30.00"));
        String id = nextId("RC");
        String methodKey = defaultText(request == null ? null : request.methodKey(), "balance-demo");
        String methodName = defaultText(request == null ? null : request.methodName(), "演示充值");
        jdbc.update("""
                INSERT INTO recharge_records (id, user_id, amount, method_key, method_name, status)
                VALUES (?, ?, ?, ?, ?, 'PAID')
                """, id, userId, amount, methodKey, methodName);
        jdbc.update("UPDATE app_users SET balance = balance + ? WHERE id = ?", amount, userId);
        return new RechargeResponse(id, amount, formatMoney(amount), methodKey, methodName, "PAID", LocalDateTime.now());
    }

    @Transactional
    public List<RechargeResponse> listRecharges(String authorization, String deviceId) {
        long userId = requestUserId(authorization, deviceId);
        return listRechargesByUserId(userId);
    }

    @Transactional
    public List<RechargeResponse> listRecharges(String deviceId) {
        long userId = findOrCreateUser(deviceId);
        return listRechargesByUserId(userId);
    }

    private List<RechargeResponse> listRechargesByUserId(long userId) {
        return jdbc.query("""
                SELECT id, amount, method_key, method_name, status, created_at
                FROM recharge_records
                WHERE user_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> new RechargeResponse(
                rs.getString("id"),
                rs.getBigDecimal("amount"),
                formatMoney(rs.getBigDecimal("amount")),
                repair(rs.getString("method_key")),
                repair(rs.getString("method_name")),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), userId);
    }

    @Transactional
    public H5RefundRequestResponse requestRefund(H5RefundRequest request, String authorization) {
        String orderId = safe(request == null ? null : request.orderId());
        String reason = safe(request == null ? null : request.reason());
        if (orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }
        if (reason.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund reason is required");
        }
        if (reason.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund reason is too long");
        }

        long userId = requireActiveUser(requestUserId(authorization, request == null ? null : request.deviceId()));
        RefundOrderRow order = jdbc.query("""
                SELECT id, user_id, amount, status
                FROM orders
                WHERE id = ? AND user_id = ?
                """, (rs, rowNum) -> new RefundOrderRow(
                rs.getString("id"),
                rs.getLong("user_id"),
                rs.getBigDecimal("amount"),
                rs.getString("status")
        ), orderId, userId).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        Optional<RefundRequestRow> latest = latestRefundRequest(orderId, userId);
        if (latest.isPresent() && "COMPLETED".equals(latest.get().status())) {
            return toRefundResponse(latest.get());
        }
        if (!"PAID".equalsIgnoreCase(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only paid orders can request a refund");
        }
        if (latest.isPresent() && List.of("PENDING_REVIEW", "APPROVED").contains(latest.get().status())) {
            return toRefundResponse(latest.get());
        }

        String requestId = nextId("REF");
        jdbc.update("""
                INSERT INTO h5_refund_requests (id, order_id, user_id, amount, reason, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING_REVIEW')
                """, requestId, orderId, userId, order.amount(), reason);
        jdbc.update("""
                INSERT INTO admin_order_events
                (order_id, event_type, from_status, to_status, amount, reason, actor_username)
                VALUES (?, 'REFUND_REQUESTED', 'PAID', 'PAID', ?, ?, 'h5-user')
                """, orderId, order.amount(), reason);
        return refundRequestById(requestId, userId);
    }

    @Transactional(readOnly = true)
    public List<H5RefundRequestResponse> listRefundRequests(String authorization, String deviceId) {
        long userId = requireActiveUser(requestUserId(authorization, deviceId));
        return jdbc.query("""
                SELECT id, order_id, amount, reason, status, review_reason, created_at, reviewed_at
                FROM h5_refund_requests
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 30
                """, (rs, rowNum) -> new H5RefundRequestResponse(
                rs.getString("id"),
                rs.getString("order_id"),
                rs.getBigDecimal("amount"),
                repair(rs.getString("reason")),
                rs.getString("status"),
                repair(rs.getString("review_reason")),
                refundTime(rs.getTimestamp("created_at")),
                refundTime(rs.getTimestamp("reviewed_at"))
        ), userId);
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String authorization) {
        if (!demoPaymentEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo payment is disabled");
        }

        long userId = requestUserId(authorization, request == null ? null : request.deviceId());
        long dramaId = request == null || request.dramaId() == null ? defaultDramaId() : request.dramaId();
        BigDecimal amount = resolvePaymentAmount(request);
        String methodKey = defaultText(request == null ? null : request.methodKey(), "balance");
        String methodName = defaultText(request == null ? null : request.methodName(), "演示余额");
        String id = nextId("PAY");
        jdbc.update("""
                INSERT INTO h5_payments
                (id, user_id, drama_id, storyline_id, amount, method_key, method_name, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """, id, userId, dramaId, nullableText(request == null ? null : request.storylineId()), amount, methodKey, methodName);
        recordPaymentEvent(id, "CREATED", "PENDING", null, "Payment order created");
        return new PaymentResponse(id, "PENDING", amount, formatMoney(amount), methodKey, methodName, "demo://payment/" + id);
    }

    @Transactional(readOnly = true)
    public PaymentSettlementResponse getPaymentSettlement(String paymentId, String authorization) {
        long userId = userIdFromAuthorization(authorization)
                .map(this::requireActiveUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "H5 login is required"));
        PaymentRow payment = paymentRow(paymentId);
        if (payment.userId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment does not belong to current user");
        }
        return paymentSettlementById(payment.id());
    }

    @Transactional
    public PaymentResponse paymentCallback(PaymentCallbackRequest request) {
        if (request == null || request.paymentId() == null || request.paymentId().isBlank()) {
            throw new IllegalArgumentException("paymentId is required");
        }
        String paymentId = request.paymentId().trim();
        String status = normalizePaymentStatus(request.status());
        PaymentRow payment = paymentRow(paymentId);
        String providerTradeNo = safe(request.providerTradeNo());
        recordPaymentEvent(paymentId, "CALLBACK_RECEIVED", status, providerTradeNo, "Payment callback received");
        if ("REFUNDED".equalsIgnoreCase(payment.status())) {
            return paymentById(paymentId);
        }
        if ("PAID".equals(status)) {
            ensurePaymentStoryline(payment);
        }
        int updated = jdbc.update("""
                UPDATE h5_payments
                SET status = ?,
                    provider_trade_no = COALESCE(NULLIF(?, ''), provider_trade_no),
                    paid_at = CASE WHEN ? = 'PAID' THEN CURRENT_TIMESTAMP ELSE paid_at END
                WHERE id = ? AND status IN ('PENDING', 'FAILED', 'CANCELLED')
                """, status, providerTradeNo, status, paymentId);
        if ("PAID".equals(status) && updated > 0) {
            settleBalanceIfNeeded(payment);
            grantPaymentEntitlement(paymentId);
            recordPaymentEvent(paymentId, "ENTITLEMENT_GRANTED", "PAID", providerTradeNo, "Payment entitlement granted");
        }
        return paymentById(paymentId);
    }

    @Transactional
    public PaymentSettlementResponse simulatePaymentSuccess(String paymentId, String authorization) {
        if (!demoPaymentEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo payment is disabled");
        }

        long userId = userIdFromAuthorization(authorization)
                .map(this::requireActiveUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "H5 login is required"));
        PaymentRow payment = paymentRow(paymentId);
        if (payment.userId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment does not belong to current user");
        }

        if ("PAID".equalsIgnoreCase(payment.status())) {
            return paymentSettlementById(payment.id());
        }
        if (!"PENDING".equalsIgnoreCase(payment.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not pending");
        }

        ensurePaymentStoryline(payment);
        int updated = jdbc.update("""
                UPDATE h5_payments
                SET status = 'PAID',
                    provider_trade_no = COALESCE(provider_trade_no, ?),
                    paid_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING'
                """, "local-" + payment.id(), payment.id());
        if (updated > 0) {
            settleBalanceIfNeeded(payment);
            grantPaymentEntitlement(payment.id());
            recordPaymentEvent(payment.id(), "SIMULATE_SUCCESS", "PAID", "local-" + payment.id(), "Local simulated payment completed");
            recordPaymentEvent(payment.id(), "ENTITLEMENT_GRANTED", "PAID", "local-" + payment.id(), "Payment entitlement granted");
        }
        return paymentSettlementById(payment.id());
    }

    @Transactional
    public InteractionStateResponse interactionState(String authorization, String deviceId, Long dramaId, String episodeId) {
        long userId = requestUserId(authorization, deviceId);
        boolean followed = dramaId != null && exists("""
                SELECT COUNT(*)
                FROM user_follows
                WHERE user_id = ? AND drama_id = ?
                """, userId, dramaId);
        boolean liked = episodeId != null && exists("""
                SELECT COUNT(*)
                FROM episode_likes
                WHERE user_id = ? AND episode_id = ?
                """, userId, episodeId);
        return new InteractionStateResponse(
                true,
                dramaId,
                episodeId,
                followed,
                liked,
                count("SELECT COUNT(*) FROM user_follows WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM episode_likes WHERE user_id = ?", userId),
                listFollowedDramas(authorization, deviceId),
                listLikedEpisodes(authorization, deviceId)
        );
    }

    @Transactional
    public InteractionStateResponse followDrama(String authorization, String deviceId, long dramaId) {
        long userId = requestUserId(authorization, deviceId);
        jdbc.update("INSERT IGNORE INTO user_follows (user_id, drama_id) VALUES (?, ?)", userId, dramaId);
        return interactionState(authorization, deviceId, dramaId, null);
    }

    @Transactional
    public List<FollowedDramaResponse> listFollowedDramas(String authorization, String deviceId) {
        long userId = requestUserId(authorization, deviceId);
        return jdbc.query("""
                SELECT d.id, d.title, d.tag, d.episode_count, d.cover_url, f.created_at
                FROM user_follows f
                JOIN dramas d ON d.id = f.drama_id
                WHERE f.user_id = ?
                ORDER BY f.created_at DESC
                """, (rs, rowNum) -> new FollowedDramaResponse(
                rs.getLong("id"),
                repair(rs.getString("title")),
                repair(rs.getString("tag")),
                rs.getInt("episode_count"),
                rs.getString("cover_url"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), userId);
    }

    @Transactional
    public InteractionStateResponse unfollowDrama(String authorization, String deviceId, long dramaId) {
        long userId = requestUserId(authorization, deviceId);
        jdbc.update("DELETE FROM user_follows WHERE user_id = ? AND drama_id = ?", userId, dramaId);
        return interactionState(authorization, deviceId, dramaId, null);
    }

    @Transactional
    public InteractionStateResponse likeEpisode(String authorization, String deviceId, String episodeId) {
        long userId = requestUserId(authorization, deviceId);
        jdbc.update("INSERT IGNORE INTO episode_likes (user_id, episode_id) VALUES (?, ?)", userId, episodeId);
        return interactionState(authorization, deviceId, null, episodeId);
    }

    @Transactional
    public List<LikedEpisodeResponse> listLikedEpisodes(String authorization, String deviceId) {
        long userId = requestUserId(authorization, deviceId);
        return jdbc.query("""
                SELECT e.id, e.drama_id, d.title AS drama_title, e.storyline_id,
                       s.name AS storyline_name, e.episode_no, e.title AS episode_title,
                       e.cover_url, l.created_at
                FROM episode_likes l
                JOIN episodes e ON e.id = l.episode_id
                JOIN dramas d ON d.id = e.drama_id
                LEFT JOIN storylines s ON s.id = e.storyline_id
                WHERE l.user_id = ?
                ORDER BY l.created_at DESC
                """, (rs, rowNum) -> new LikedEpisodeResponse(
                rs.getString("id"),
                rs.getLong("drama_id"),
                repair(rs.getString("drama_title")),
                rs.getString("storyline_id"),
                repair(rs.getString("storyline_name")),
                rs.getInt("episode_no"),
                repair(rs.getString("episode_title")),
                rs.getString("cover_url"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), userId);
    }

    @Transactional
    public InteractionStateResponse unlikeEpisode(String authorization, String deviceId, String episodeId) {
        long userId = requestUserId(authorization, deviceId);
        jdbc.update("DELETE FROM episode_likes WHERE user_id = ? AND episode_id = ?", userId, episodeId);
        return interactionState(authorization, deviceId, null, episodeId);
    }

    @Transactional
    public ApiOkResponse recordPlayEvent(PlayEventRequest request, String authorization) {
        String deviceId = normalizeDeviceId(request == null ? null : request.deviceId());
        Long userId = hasBearerToken(authorization) ? requestUserId(authorization, deviceId) : null;
        String payload = writeJson(request == null ? null : request.payload());
        if (payload == null) {
            jdbc.update("""
                    INSERT INTO play_events (user_id, device_id, event_type, drama_id, episode_id, storyline_id, payload)
                    VALUES (?, ?, ?, ?, ?, ?, NULL)
                    """,
                    userId, deviceId, defaultText(request == null ? null : request.eventType(), "unknown"),
                    request == null ? null : request.dramaId(),
                    request == null ? null : request.episodeId(),
                    request == null ? null : request.storylineId());
        } else {
            jdbc.update("""
                    INSERT INTO play_events (user_id, device_id, event_type, drama_id, episode_id, storyline_id, payload)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                    """,
                    userId, deviceId, defaultText(request.eventType(), "unknown"),
                    request.dramaId(), request.episodeId(), request.storylineId(), payload);
        }
        return new ApiOkResponse(true);
    }

    @Transactional
    public long requestUserId(String authorization, String deviceId) {
        if (hasBearerToken(authorization)) {
            return userIdFromAuthorization(authorization)
                    .map(this::requireActiveUser)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "H5 login is required"));
        }
        return requireActiveUser(findOrCreateUser(deviceId));
    }

    public Optional<Long> authenticatedUserId(String authorization) {
        return userIdFromAuthorization(authorization);
    }

    private long requireActiveUser(long userId) {
        String status = jdbc.query("SELECT status FROM app_users WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"), userId).stream().findFirst().orElse("NORMAL");
        if ("FROZEN".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is frozen");
        }
        return userId;
    }

    private H5AuthResponse authResponse(long userId) {
        TokenPair token = issueToken(userId);
        return new H5AuthResponse(token.token(), token.refreshToken(), TOKEN_SECONDS, profileByUserId(userId));
    }

    private TokenPair issueToken(long userId) {
        String token = "h5_" + shortToken();
        String refreshToken = "h5r_" + shortToken();
        jdbc.update("""
                INSERT INTO h5_auth_tokens (token, refresh_token, user_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, token, refreshToken, userId, Timestamp.valueOf(LocalDateTime.now().plusSeconds(TOKEN_SECONDS)));
        return new TokenPair(token, refreshToken);
    }

    private Optional<Long> userIdFromAuthorization(String authorization) {
        String token = bearerToken(authorization);
        if (token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT user_id
                FROM h5_auth_tokens
                WHERE token = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong("user_id"), token).stream().findFirst();
    }

    private boolean hasBearerToken(String authorization) {
        return !bearerToken(authorization).isBlank();
    }

    private String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return "";
        }
        return authorization.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private H5ProfileResponse profileByUserId(long userId) {
        ensurePreferences(userId);
        UserRow user = userRow(userId);
        return new H5ProfileResponse(
                user.id(),
                user.deviceId(),
                maskPhone(dataProtection.decrypt(user.phone())),
                repair(user.nickname()),
                user.avatar(),
                user.level(),
                user.balance(),
                user.status(),
                user.phoneBound(),
                paidAmount(userId),
                count("SELECT COUNT(*) FROM watch_history WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM user_unlocks WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId),
                jdbc.query("SELECT drama_id FROM user_follows WHERE user_id = ? ORDER BY created_at DESC",
                        (rs, rowNum) -> rs.getLong("drama_id"), userId),
                jdbc.query("SELECT episode_id FROM episode_likes WHERE user_id = ? ORDER BY created_at DESC",
                        (rs, rowNum) -> rs.getString("episode_id"), userId),
                preferencesByUserId(userId),
                user.createdAt(),
                user.lastActiveAt()
        );
    }

    private UserRow userRow(long userId) {
        return jdbc.query("""
                SELECT id, device_id, phone, nickname, avatar, level, balance,
                       status, phone_bound, created_at, last_active_at
                FROM app_users
                WHERE id = ?
                """, (rs, rowNum) -> new UserRow(
                rs.getLong("id"),
                rs.getString("device_id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getString("avatar"),
                rs.getString("level"),
                rs.getBigDecimal("balance"),
                rs.getString("status"),
                rs.getBoolean("phone_bound"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("last_active_at"))
        ), userId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }

    private void validateSmsCode(String phone, String scene, String code, boolean allowLoginScene) {
        String safeCode = safe(code);
        if (safeCode.isBlank()) {
            throw new IllegalArgumentException("sms code is required");
        }

        String phoneHash = dataProtection.phoneFingerprint(phone);
        String primaryScene = normalizeSmsScene(scene);
        List<SmsCodeRow> matches;
        if (allowLoginScene && !"login".equals(primaryScene)) {
            matches = jdbc.query("""
                    SELECT id, code_hash
                    FROM h5_sms_codes
                    WHERE phone_hash = ?
                      AND scene IN (?, 'login')
                      AND consumed = FALSE
                      AND expires_at > CURRENT_TIMESTAMP
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, (rs, rowNum) -> new SmsCodeRow(
                    rs.getLong("id"),
                    rs.getString("code_hash")
            ), phoneHash, primaryScene);
        } else {
            matches = jdbc.query("""
                    SELECT id, code_hash
                    FROM h5_sms_codes
                    WHERE phone_hash = ?
                      AND scene = ?
                      AND consumed = FALSE
                      AND expires_at > CURRENT_TIMESTAMP
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, (rs, rowNum) -> new SmsCodeRow(
                    rs.getLong("id"),
                    rs.getString("code_hash")
            ), phoneHash, primaryScene);
        }

        SmsCodeRow matched = matches.stream()
                .filter(row -> constantTimeEquals(row.codeHash(), hashSmsCode(phoneHash, safeCode)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sms code is invalid or expired"));
        jdbc.update("UPDATE h5_sms_codes SET consumed = TRUE, used_at = CURRENT_TIMESTAMP WHERE id = ?", matched.id());
    }

    private void enforceSmsRateLimit(String phoneHash, String scene) {
        String key = phoneHash + ":" + scene;
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        SmsRateLimit existing = smsRateLimits.get(key);
        if (existing != null && today.equals(existing.day())) {
            if (existing.lastSentAt().plusSeconds(SMS_MIN_INTERVAL_SECONDS).isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "SMS code was sent recently");
            }
            if (existing.count() >= SMS_DAILY_LIMIT) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "SMS daily limit exceeded");
            }
            smsRateLimits.put(key, new SmsRateLimit(today, existing.count() + 1, now));
            return;
        }
        smsRateLimits.put(key, new SmsRateLimit(today, 1, now));
    }

    private Optional<Long> findUserIdByPhoneHash(String phoneHash) {
        if (phoneHash == null || phoneHash.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT id
                FROM app_users
                WHERE phone_hash = ?
                ORDER BY id
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), phoneHash).stream().findFirst();
    }

    private Optional<UserPasswordRow> userPasswordByPhoneHash(String phoneHash) {
        if (phoneHash == null || phoneHash.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT id, password_salt, password_hash
                FROM app_users
                WHERE phone_hash = ?
                ORDER BY id
                LIMIT 1
                """, (rs, rowNum) -> new UserPasswordRow(
                rs.getLong("id"),
                rs.getString("password_salt"),
                rs.getString("password_hash")
        ), phoneHash).stream().findFirst();
    }

    private Optional<UserPasswordRow> userPasswordByUserId(long userId) {
        return jdbc.query("""
                SELECT id, password_salt, password_hash
                FROM app_users
                WHERE id = ?
                """, (rs, rowNum) -> new UserPasswordRow(
                rs.getLong("id"),
                rs.getString("password_salt"),
                rs.getString("password_hash")
        ), userId).stream().findFirst();
    }

    private PasswordHashService.EncodedPassword passwordHash(String password) {
        return passwordHashService.encode(password);
    }

    private boolean passwordMatches(String salt, String expectedHash, String password) {
        return passwordHashService.matches(salt, expectedHash, password);
    }

    private void upgradePasswordHashIfNeeded(UserPasswordRow user, String password) {
        if (!passwordHashService.needsUpgrade(user.passwordSalt(), user.passwordHash())) {
            return;
        }
        PasswordHashService.EncodedPassword passwordHash = passwordHash(password);
        jdbc.update("UPDATE app_users SET password_salt = ?, password_hash = ? WHERE id = ?",
                passwordHash.salt(), passwordHash.hash(), user.userId());
    }

    private long findOrCreateUser(String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        jdbc.update("""
                INSERT INTO app_users (device_id, nickname, avatar, last_active_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE last_active_at = CURRENT_TIMESTAMP
                """, normalized, defaultNickname(normalized), DEFAULT_AVATAR);
        long userId = findUserId(normalized).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("user creation failed"));
        ensurePreferences(userId);
        return userId;
    }

    private List<Long> findUserId(String deviceId) {
        return jdbc.query("SELECT id FROM app_users WHERE device_id = ?",
                (rs, rowNum) -> rs.getLong("id"), normalizeDeviceId(deviceId));
    }

    private void ensurePreferences(long userId) {
        jdbc.update("INSERT IGNORE INTO user_preferences (user_id) VALUES (?)", userId);
    }

    private H5PreferencesResponse preferencesByUserId(long userId) {
        return jdbc.query("""
                SELECT auto_play_next, unlock_reminder, muted
                FROM user_preferences
                WHERE user_id = ?
                """, (rs, rowNum) -> new H5PreferencesResponse(
                rs.getBoolean("auto_play_next"),
                rs.getBoolean("unlock_reminder"),
                rs.getBoolean("muted")
        ), userId).stream().findFirst().orElse(new H5PreferencesResponse(true, true, true));
    }

    private EpisodeInfo episodeInfo(String episodeId) {
        return jdbc.query("""
                SELECT id, drama_id, episode_no, storyline_id
                FROM episodes
                WHERE id = ?
                """, (rs, rowNum) -> new EpisodeInfo(
                rs.getString("id"),
                rs.getLong("drama_id"),
                rs.getInt("episode_no"),
                rs.getString("storyline_id")
        ), episodeId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("episode not found: " + episodeId));
    }

    private BigDecimal resolvePaymentAmount(PaymentRequest request) {
        BigDecimal fallback = positiveAmount(request == null ? null : request.amount(), new BigDecimal("6.00"));
        String episodeId = request == null ? null : request.episodeId();
        if (episodeId == null || episodeId.isBlank()) {
            return fallback;
        }

        return jdbc.query("""
                SELECT unlock_price
                FROM episodes
                WHERE id = ? AND unlock_price IS NOT NULL
                """, (rs, rowNum) -> rs.getBigDecimal("unlock_price"), episodeId)
                .stream()
                .findFirst()
                .map((amount) -> positiveAmount(amount, fallback))
                .orElse(fallback);
    }

    private long defaultDramaId() {
        return jdbc.query("""
                SELECT id FROM dramas
                WHERE status = 'PUBLISHED'
                ORDER BY sort_order, id
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id")).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No published dramas found"));
    }

    private PaymentResponse paymentById(String paymentId) {
        return jdbc.query("""
                SELECT id, status, amount, method_key, method_name
                FROM h5_payments
                WHERE id = ?
                """, (rs, rowNum) -> new PaymentResponse(
                rs.getString("id"),
                rs.getString("status"),
                rs.getBigDecimal("amount"),
                formatMoney(rs.getBigDecimal("amount")),
                repair(rs.getString("method_key")),
                repair(rs.getString("method_name")),
                "demo://payment/" + rs.getString("id")
        ), paymentId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("payment not found: " + paymentId));
    }

    private PaymentSettlementResponse paymentSettlementById(String paymentId) {
        StorylineResponse line = jdbc.query("""
                SELECT s.id, s.drama_id, s.name, s.rarity, s.description, s.cover_url
                FROM h5_payments p
                JOIN storylines s ON s.id = p.storyline_id
                WHERE p.id = ?
                """, (rs, rowNum) -> new StorylineResponse(
                rs.getString("id"),
                rs.getLong("drama_id"),
                repair(rs.getString("name")),
                repair(rs.getString("rarity")),
                repair(rs.getString("description")),
                publicUrl(rs.getString("cover_url"))
        ), paymentId).stream().findFirst().orElse(null);

        UnlockOrderResponse order = jdbc.query("""
                SELECT orders.id, orders.title, orders.amount, orders.payment_method, orders.payment_method_key,
                       orders.status,
                       DATE_FORMAT(COALESCE(orders.paid_at, orders.created_at), '%m-%d %H:%i') AS time_text,
                       refund_request.status AS refund_status,
                       refund_request.reason AS refund_reason
                FROM orders
                LEFT JOIN (
                    SELECT order_id, status, reason,
                           ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY created_at DESC, id DESC) AS rank_no
                    FROM h5_refund_requests
                ) refund_request ON refund_request.order_id = orders.id AND refund_request.rank_no = 1
                WHERE orders.id = ?
                """, (rs, rowNum) -> new UnlockOrderResponse(
                rs.getString("id"),
                repair(rs.getString("title")),
                formatMoney(rs.getBigDecimal("amount")),
                repair(rs.getString("payment_method")),
                repair(rs.getString("payment_method_key")),
                rs.getString("time_text"),
                rs.getString("status"),
                rs.getString("refund_status"),
                repair(rs.getString("refund_reason"))
        ), paymentId).stream().findFirst().orElse(null);

        return new PaymentSettlementResponse(
                paymentById(paymentId),
                line,
                order,
                line == null ? "Payment settled" : "Unlocked: " + line.name()
        );
    }

    private Optional<RefundRequestRow> latestRefundRequest(String orderId, long userId) {
        return jdbc.query("""
                SELECT id, order_id, amount, reason, status, review_reason, reviewer_username, created_at, reviewed_at
                FROM h5_refund_requests
                WHERE order_id = ? AND user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> new RefundRequestRow(
                rs.getString("id"),
                rs.getString("order_id"),
                rs.getBigDecimal("amount"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("review_reason"),
                rs.getString("reviewer_username"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("reviewed_at")
        ), orderId, userId).stream().findFirst();
    }

    private H5RefundRequestResponse refundRequestById(String requestId, long userId) {
        return jdbc.query("""
                SELECT id, order_id, amount, reason, status, review_reason, created_at, reviewed_at
                FROM h5_refund_requests
                WHERE id = ? AND user_id = ?
                """, (rs, rowNum) -> new H5RefundRequestResponse(
                rs.getString("id"),
                rs.getString("order_id"),
                rs.getBigDecimal("amount"),
                repair(rs.getString("reason")),
                rs.getString("status"),
                repair(rs.getString("review_reason")),
                refundTime(rs.getTimestamp("created_at")),
                refundTime(rs.getTimestamp("reviewed_at"))
        ), requestId, userId).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request not found"));
    }

    private H5RefundRequestResponse toRefundResponse(RefundRequestRow request) {
        return new H5RefundRequestResponse(
                request.id(),
                request.orderId(),
                request.amount(),
                repair(request.reason()),
                request.status(),
                repair(request.reviewReason()),
                refundTime(request.createdAt()),
                refundTime(request.reviewedAt())
        );
    }

    private String refundTime(Timestamp timestamp) {
        LocalDateTime value = toLocalDateTime(timestamp);
        return value == null ? "" : value.format(REFUND_TIME);
    }

    private PaymentRow paymentRow(String paymentId) {
        String safePaymentId = safe(paymentId);
        if (safePaymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId is required");
        }

        return jdbc.query("""
                SELECT id, user_id, drama_id, storyline_id, amount, method_key, status
                FROM h5_payments
                WHERE id = ?
                """, (rs, rowNum) -> new PaymentRow(
                rs.getString("id"),
                rs.getLong("user_id"),
                rs.getLong("drama_id"),
                rs.getString("storyline_id"),
                positiveAmount(rs.getBigDecimal("amount"), BigDecimal.ZERO),
                rs.getString("method_key"),
                rs.getString("status")
        ), safePaymentId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("payment not found: " + safePaymentId));
    }

    private StorylineResponse ensurePaymentStoryline(PaymentRow payment) {
        if (payment.storylineId() != null && !payment.storylineId().isBlank()) {
            return storylineById(payment.storylineId());
        }

        StorylineResponse line = selectPaymentStoryline(payment.userId(), payment.dramaId());
        jdbc.update("""
                UPDATE h5_payments
                SET storyline_id = ?
                WHERE id = ? AND (storyline_id IS NULL OR storyline_id = '')
                """, line.id(), payment.id());
        return line;
    }

    private StorylineResponse storylineById(String storylineId) {
        return jdbc.query("""
                SELECT id, drama_id, name, rarity, description, cover_url
                FROM storylines
                WHERE id = ?
                """, (rs, rowNum) -> new StorylineResponse(
                rs.getString("id"),
                rs.getLong("drama_id"),
                repair(rs.getString("name")),
                repair(rs.getString("rarity")),
                repair(rs.getString("description")),
                publicUrl(rs.getString("cover_url"))
        ), storylineId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Storyline not found: " + storylineId));
    }

    private StorylineResponse selectPaymentStoryline(long userId, long dramaId) {
        List<PaymentStorylineCandidate> candidates = jdbc.query("""
                SELECT s.id, s.drama_id, s.name, s.rarity, s.description, s.cover_url, s.weight
                FROM storylines s
                WHERE s.drama_id = ?
                  AND s.status = 'ENABLED'
                  AND NOT EXISTS (
                    SELECT 1 FROM user_unlocks u
                    WHERE u.user_id = ? AND u.storyline_id = s.id
                  )
                ORDER BY s.sort_order, s.id
                """, (rs, rowNum) -> new PaymentStorylineCandidate(
                new StorylineResponse(
                        rs.getString("id"),
                        rs.getLong("drama_id"),
                        repair(rs.getString("name")),
                        repair(rs.getString("rarity")),
                        repair(rs.getString("description")),
                        publicUrl(rs.getString("cover_url"))
                ),
                positiveWeight(rs.getBigDecimal("weight"))
        ), dramaId, userId);

        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All storylines are already unlocked");
        }
        return selectWeighted(candidates);
    }

    private StorylineResponse selectWeighted(List<PaymentStorylineCandidate> candidates) {
        BigDecimal total = candidates.stream()
                .map(PaymentStorylineCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).line();
        }

        BigDecimal cursor = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())
                .multiply(total);
        BigDecimal accumulated = BigDecimal.ZERO;
        for (PaymentStorylineCandidate candidate : candidates) {
            accumulated = accumulated.add(candidate.weight());
            if (cursor.compareTo(accumulated) <= 0) {
                return candidate.line();
            }
        }
        return candidates.get(candidates.size() - 1).line();
    }

    private BigDecimal positiveWeight(BigDecimal weight) {
        return weight == null || weight.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : weight;
    }

    private void recordPaymentEvent(String paymentId, String eventType, String status, String providerTradeNo, String message) {
        jdbc.update("""
                INSERT INTO h5_payment_events (payment_id, event_type, status, provider_trade_no, message)
                VALUES (?, ?, ?, NULLIF(?, ''), ?)
                """,
                paymentId,
                eventType,
                status,
                safe(providerTradeNo),
                safe(message));
    }

    private void settleBalanceIfNeeded(PaymentRow payment) {
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

    private boolean isBalancePayment(String methodKey) {
        String normalized = safe(methodKey).toLowerCase(Locale.ROOT);
        return "balance".equals(normalized) || "wallet".equals(normalized);
    }

    private void grantPaymentEntitlement(String paymentId) {
        int insertedOrders = jdbc.update("""
                INSERT IGNORE INTO orders
                (id, user_id, drama_id, storyline_id, title, amount, payment_method, payment_method_key, status, paid_at)
                SELECT p.id, p.user_id, p.drama_id, p.storyline_id,
                       COALESCE(s.name, '支付订单'), p.amount, p.method_name, p.method_key, 'PAID', CURRENT_TIMESTAMP
                FROM h5_payments p
                LEFT JOIN storylines s ON s.id = p.storyline_id
                WHERE p.id = ? AND p.status = 'PAID'
                """, paymentId);
        jdbc.update("""
                INSERT IGNORE INTO user_unlocks (user_id, drama_id, storyline_id, order_id)
                SELECT user_id, drama_id, storyline_id, id
                FROM h5_payments
                WHERE id = ? AND status = 'PAID' AND storyline_id IS NOT NULL AND storyline_id <> ''
                """, paymentId);
        if (insertedOrders > 0) {
            jdbc.update("""
                    UPDATE app_users u
                    JOIN h5_payments p ON p.user_id = u.id
                    SET u.paid_amount = u.paid_amount + p.amount
                    WHERE p.id = ?
                    """, paymentId);
        }
    }

    private BigDecimal paidAmount(long userId) {
        BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM orders WHERE user_id = ? AND status = 'PAID'",
                BigDecimal.class, userId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private int count(String sql, long userId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, userId);
        return value == null ? 0 : value;
    }

    private boolean exists(String sql, long userId, Object targetId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, userId, targetId);
        return value != null && value > 0;
    }

    private String normalizeDeviceId(String deviceId) {
        return deviceId == null || deviceId.isBlank() ? DEFAULT_DEVICE_ID : deviceId.trim();
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim().replaceAll("[\\s()\\-]", "");
    }

    private String normalizeSmsScene(String scene) {
        String text = defaultText(scene, "login").toLowerCase(Locale.ROOT);
        if (text.length() > 32) {
            return text.substring(0, 32);
        }
        return text;
    }

    private String hashSmsCode(String phoneHash, String code) {
        return sha256(phoneHash + ":" + code);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(defaultText(value, "").getBytes(StandardCharsets.UTF_8));
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

    private String defaultNickname(String deviceId) {
        return "游客 " + Math.abs(deviceId.hashCode() % 10000);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimTrailingSlash(String value) {
        String text = defaultText(value, "http://localhost:8081");
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String publicUrl(String path) {
        if (path == null || path.isBlank()) {
            return publicBaseUrl;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return path;
        }
        return publicBaseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String extensionFrom(String originalName, String contentType) {
        String lowerName = defaultText(originalName, "").toLowerCase(Locale.ROOT);
        int dot = lowerName.lastIndexOf('.');
        if (dot >= 0) {
            String extension = lowerName.substring(dot);
            if (List.of(".jpg", ".jpeg", ".png", ".webp", ".gif").contains(extension)) {
                return extension;
            }
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Date.valueOf(LocalDate.parse(value.trim()));
        } catch (Exception ex) {
            return null;
        }
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private BigDecimal positiveAmount(BigDecimal value, BigDecimal fallback) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return fallback;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal amount) {
        return "¥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
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

    String normalizePaymentStatus(String status) {
        String normalized = safe(status).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment status is required");
        }
        if ("SUCCESS".equals(normalized) || "PAID".equals(normalized)) {
            return "PAID";
        }
        if ("PENDING".equals(normalized) || "FAILED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment status is invalid");
    }

    private String shortToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String nextId(String prefix) {
        return prefix + LocalDateTime.now().format(ID_TIME) + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record TokenPair(String token, String refreshToken) {
    }

    private record SmsCodeRow(Long id, String codeHash) {
    }

    private record SmsRateLimit(LocalDate day, int count, LocalDateTime lastSentAt) {
    }

    private record UserPasswordRow(Long userId, String passwordSalt, String passwordHash) {
    }

    private record UserRow(
            Long id,
            String deviceId,
            String phone,
            String nickname,
            String avatar,
            String level,
            BigDecimal balance,
            String status,
            Boolean phoneBound,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt
    ) {
    }

    private record EpisodeInfo(String id, Long dramaId, Integer number, String storylineId) {
    }

    private record RefundOrderRow(String id, long userId, BigDecimal amount, String status) {
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
            long dramaId,
            String storylineId,
            BigDecimal amount,
            String methodKey,
            String status
    ) {
    }

    private record PaymentStorylineCandidate(StorylineResponse line, BigDecimal weight) {
    }
}
