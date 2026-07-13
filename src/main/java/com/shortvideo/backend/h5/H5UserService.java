package com.shortvideo.backend.h5;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.h5.dto.ApiOkResponse;
import com.shortvideo.backend.h5.dto.BindPhoneResponse;
import com.shortvideo.backend.h5.dto.GuestLoginRequest;
import com.shortvideo.backend.h5.dto.H5AuthResponse;
import com.shortvideo.backend.h5.dto.H5PreferencesRequest;
import com.shortvideo.backend.h5.dto.H5PreferencesResponse;
import com.shortvideo.backend.h5.dto.H5ProfileResponse;
import com.shortvideo.backend.h5.dto.OauthLoginRequest;
import com.shortvideo.backend.h5.dto.PasswordLoginRequest;
import com.shortvideo.backend.h5.dto.PaymentCallbackRequest;
import com.shortvideo.backend.h5.dto.PaymentRequest;
import com.shortvideo.backend.h5.dto.PaymentResponse;
import com.shortvideo.backend.h5.dto.PhoneLoginRequest;
import com.shortvideo.backend.h5.dto.PlayEventRequest;
import com.shortvideo.backend.h5.dto.RechargeRequest;
import com.shortvideo.backend.h5.dto.RechargeResponse;
import com.shortvideo.backend.h5.dto.RefreshTokenRequest;
import com.shortvideo.backend.h5.dto.RefreshTokenResponse;
import com.shortvideo.backend.h5.dto.RegisterRequest;
import com.shortvideo.backend.h5.dto.SmsCodeRequest;
import com.shortvideo.backend.h5.dto.SmsCodeResponse;
import com.shortvideo.backend.h5.dto.UpdateProfileRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureResponse;
import com.shortvideo.backend.h5.dto.WalletResponse;
import com.shortvideo.backend.h5.dto.WatchHistoryRequest;
import com.shortvideo.backend.h5.dto.WatchHistoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class H5UserService {

    private static final String DEFAULT_DEVICE_ID = "demo-device";
    private static final int TOKEN_SECONDS = 7200;
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public H5UserService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public SmsCodeResponse sendCode(SmsCodeRequest request) {
        String phone = normalizePhone(request == null ? null : request.phone());
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        return new SmsCodeResponse(true, true, 60, 60, "sms-" + shortToken());
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
        long userId = findOrCreateUser(request.deviceId());
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
        long userId = findOrCreateUser(request.deviceId());
        jdbc.update("UPDATE app_users SET phone = ?, phone_bound = TRUE WHERE id = ?", account, userId);
        return authResponse(userId);
    }

    @Transactional
    public H5AuthResponse register(RegisterRequest request) {
        String phone = normalizePhone(request == null ? null : request.phone());
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        long userId = findOrCreateUser(request.deviceId());
        jdbc.update("""
                UPDATE app_users
                SET phone = ?, phone_bound = TRUE, nickname = ?
                WHERE id = ?
                """, phone, defaultText(request.nickname(), "追剧用户"), userId);
        return authResponse(userId);
    }

    @Transactional
    public H5AuthResponse oauthLogin(OauthLoginRequest request) {
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

    public H5ProfileResponse currentUser(String authorization, String deviceId) {
        Optional<Long> userId = userIdFromAuthorization(authorization);
        return userId.map(this::profileByUserId)
                .orElseGet(() -> profileByDevice(deviceId));
    }

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
    public BindPhoneResponse bindPhone(String authorization, String deviceId, String phone) {
        long userId = requireActiveUser(requestUserId(authorization, deviceId));
        String safePhone = normalizePhone(phone);
        if (safePhone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        jdbc.update("UPDATE app_users SET phone = ?, phone_bound = TRUE WHERE id = ?", safePhone, userId);
        return new BindPhoneResponse(true, maskPhone(safePhone));
    }

    public ApiOkResponse changePassword(String authorization) {
        requireActiveUser(requestUserId(authorization, null));
        return new ApiOkResponse(true);
    }

    public UploadSignatureResponse avatarUploadSignature(UploadSignatureRequest request) {
        String fileName = defaultText(request == null ? null : request.fileName(), "avatar.png").replace("\\", "/");
        String key = "avatars/" + shortToken() + "-" + fileName.substring(fileName.lastIndexOf('/') + 1);
        return new UploadSignatureResponse(
                "http://localhost:8080/demo-upload/" + key,
                "http://localhost:8080/static/" + key,
                600
        );
    }

    public WalletResponse wallet(String deviceId) {
        long userId = findOrCreateUser(deviceId);
        UserRow user = userRow(userId);
        return new WalletResponse(
                user.deviceId(),
                user.balance(),
                paidAmount(userId),
                count("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId),
                count("SELECT COUNT(*) FROM recharge_records WHERE user_id = ?", userId)
        );
    }

    public H5PreferencesResponse preferences(String deviceId) {
        long userId = findOrCreateUser(deviceId);
        ensurePreferences(userId);
        return preferencesByUserId(userId);
    }

    @Transactional
    public H5PreferencesResponse updatePreferences(H5PreferencesRequest request) {
        long userId = requireActiveUser(findOrCreateUser(request == null ? null : request.deviceId()));
        ensurePreferences(userId);
        H5PreferencesResponse current = preferencesByUserId(userId);
        jdbc.update("""
                UPDATE user_preferences
                SET auto_play_next = ?, unlock_reminder = ?, muted = ?
                WHERE user_id = ?
                """,
                request.autoPlayNext() == null ? current.autoPlayNext() : request.autoPlayNext(),
                request.unlockReminder() == null ? current.unlockReminder() : request.unlockReminder(),
                request.muted() == null ? current.muted() : request.muted(),
                userId);
        return preferencesByUserId(userId);
    }

    @Transactional
    public WatchHistoryResponse saveWatchHistory(WatchHistoryRequest request) {
        if (request == null || request.episodeId() == null || request.episodeId().isBlank()) {
            throw new IllegalArgumentException("episodeId is required");
        }
        long userId = requireActiveUser(findOrCreateUser(request.deviceId()));
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

        return listWatchHistory(request.deviceId()).stream()
                .filter(item -> item.episodeId().equals(request.episodeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("watch history save failed"));
    }

    public List<WatchHistoryResponse> listWatchHistory(String deviceId) {
        long userId = findOrCreateUser(deviceId);
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
                rs.getString("drama_title"),
                rs.getString("storyline_id"),
                rs.getString("storyline_name"),
                rs.getString("episode_id"),
                rs.getInt("episode_number"),
                rs.getString("episode_title"),
                rs.getString("cover_url"),
                rs.getInt("progress_seconds"),
                rs.getInt("duration_seconds"),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        ), userId);
    }

    @Transactional
    public ApiOkResponse clearWatchHistory(String deviceId) {
        jdbc.update("DELETE FROM watch_history WHERE user_id = ?", requireActiveUser(findOrCreateUser(deviceId)));
        return new ApiOkResponse(true);
    }

    @Transactional
    public RechargeResponse recharge(RechargeRequest request) {
        long userId = requireActiveUser(findOrCreateUser(request == null ? null : request.deviceId()));
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

    public List<RechargeResponse> listRecharges(String deviceId) {
        long userId = findOrCreateUser(deviceId);
        return jdbc.query("""
                SELECT id, amount, method_key, method_name, status, created_at
                FROM recharge_records
                WHERE user_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> new RechargeResponse(
                rs.getString("id"),
                rs.getBigDecimal("amount"),
                formatMoney(rs.getBigDecimal("amount")),
                rs.getString("method_key"),
                rs.getString("method_name"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), userId);
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        long userId = requireActiveUser(findOrCreateUser(request == null ? null : request.deviceId()));
        long dramaId = request == null || request.dramaId() == null ? defaultDramaId() : request.dramaId();
        BigDecimal amount = positiveAmount(request == null ? null : request.amount(), new BigDecimal("6.00"));
        String methodKey = defaultText(request == null ? null : request.methodKey(), "balance");
        String methodName = defaultText(request == null ? null : request.methodName(), "演示余额");
        String id = nextId("PAY");
        jdbc.update("""
                INSERT INTO h5_payments
                (id, user_id, drama_id, storyline_id, amount, method_key, method_name, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """, id, userId, dramaId, safe(request == null ? null : request.storylineId()), amount, methodKey, methodName);
        return new PaymentResponse(id, "PENDING", amount, formatMoney(amount), methodKey, methodName, "demo://payment/" + id);
    }

    @Transactional
    public PaymentResponse paymentCallback(PaymentCallbackRequest request) {
        if (request == null || request.paymentId() == null || request.paymentId().isBlank()) {
            throw new IllegalArgumentException("paymentId is required");
        }
        String status = normalizePaymentStatus(request.status());
        jdbc.update("""
                UPDATE h5_payments
                SET status = ?, provider_trade_no = ?, paid_at = CASE WHEN ? = 'PAID' THEN CURRENT_TIMESTAMP ELSE paid_at END
                WHERE id = ?
                """, status, safe(request.providerTradeNo()), status, request.paymentId());
        if ("PAID".equals(status)) {
            grantPaymentEntitlement(request.paymentId());
        }
        return paymentById(request.paymentId());
    }

    @Transactional
    public ApiOkResponse followDrama(String deviceId, long dramaId) {
        long userId = requireActiveUser(findOrCreateUser(deviceId));
        jdbc.update("INSERT IGNORE INTO user_follows (user_id, drama_id) VALUES (?, ?)", userId, dramaId);
        return new ApiOkResponse(true);
    }

    @Transactional
    public ApiOkResponse unfollowDrama(String deviceId, long dramaId) {
        long userId = requireActiveUser(findOrCreateUser(deviceId));
        jdbc.update("DELETE FROM user_follows WHERE user_id = ? AND drama_id = ?", userId, dramaId);
        return new ApiOkResponse(true);
    }

    @Transactional
    public ApiOkResponse likeEpisode(String deviceId, String episodeId) {
        long userId = requireActiveUser(findOrCreateUser(deviceId));
        jdbc.update("INSERT IGNORE INTO episode_likes (user_id, episode_id) VALUES (?, ?)", userId, episodeId);
        return new ApiOkResponse(true);
    }

    @Transactional
    public ApiOkResponse unlikeEpisode(String deviceId, String episodeId) {
        long userId = requireActiveUser(findOrCreateUser(deviceId));
        jdbc.update("DELETE FROM episode_likes WHERE user_id = ? AND episode_id = ?", userId, episodeId);
        return new ApiOkResponse(true);
    }

    @Transactional
    public ApiOkResponse recordPlayEvent(PlayEventRequest request) {
        String deviceId = normalizeDeviceId(request == null ? null : request.deviceId());
        Long userId = findUserId(deviceId).stream().findFirst().map(this::requireActiveUser).orElse(null);
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

    public long requestUserId(String authorization, String deviceId) {
        return userIdFromAuthorization(authorization).orElseGet(() -> findOrCreateUser(deviceId));
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
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        String token = authorization.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT user_id
                FROM h5_auth_tokens
                WHERE token = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong("user_id"), token).stream().findFirst();
    }

    private H5ProfileResponse profileByUserId(long userId) {
        ensurePreferences(userId);
        UserRow user = userRow(userId);
        return new H5ProfileResponse(
                user.id(),
                user.deviceId(),
                maskPhone(user.phone()),
                user.nickname(),
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

    private long findOrCreateUser(String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        jdbc.update("""
                INSERT INTO app_users (device_id, nickname, last_active_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE last_active_at = CURRENT_TIMESTAMP
                """, normalized, defaultNickname(normalized));
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
                rs.getString("method_key"),
                rs.getString("method_name"),
                "demo://payment/" + rs.getString("id")
        ), paymentId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("payment not found: " + paymentId));
    }

    private void grantPaymentEntitlement(String paymentId) {
        jdbc.update("""
                INSERT IGNORE INTO orders
                (id, user_id, drama_id, storyline_id, title, amount, payment_method, payment_method_key, status, paid_at)
                SELECT p.id, p.user_id, p.drama_id, p.storyline_id,
                       COALESCE(s.name, '支付订单'), p.amount, p.method_name, p.method_key, 'PAID', CURRENT_TIMESTAMP
                FROM h5_payments p
                LEFT JOIN storylines s ON s.id = p.storyline_id
                WHERE p.id = ?
                """, paymentId);
        jdbc.update("""
                INSERT IGNORE INTO user_unlocks (user_id, drama_id, storyline_id, order_id)
                SELECT user_id, drama_id, storyline_id, id
                FROM h5_payments
                WHERE id = ? AND storyline_id IS NOT NULL AND storyline_id <> ''
                """, paymentId);
        jdbc.update("""
                UPDATE app_users u
                JOIN h5_payments p ON p.user_id = u.id
                SET u.paid_amount = u.paid_amount + p.amount
                WHERE p.id = ?
                """, paymentId);
    }

    private BigDecimal paidAmount(long userId) {
        BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM orders WHERE user_id = ?",
                BigDecimal.class, userId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private int count(String sql, long userId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, userId);
        return value == null ? 0 : value;
    }

    private String normalizeDeviceId(String deviceId) {
        return deviceId == null || deviceId.isBlank() ? DEFAULT_DEVICE_ID : deviceId.trim();
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim();
    }

    private String defaultNickname(String deviceId) {
        return "游客 " + Math.abs(deviceId.hashCode() % 10000);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
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

    private String normalizePaymentStatus(String status) {
        String normalized = defaultText(status, "PAID").toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(normalized) || "PAID".equals(normalized)) {
            return "PAID";
        }
        if ("FAILED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return normalized;
        }
        return "PAID";
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
}
