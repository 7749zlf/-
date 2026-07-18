package com.shortvideo.backend.h5;

import java.net.URI;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.shortvideo.backend.h5.dto.DramaResponse;
import com.shortvideo.backend.h5.dto.DrawRequest;
import com.shortvideo.backend.h5.dto.DrawResponse;
import com.shortvideo.backend.h5.dto.EpisodeAccessRequest;
import com.shortvideo.backend.h5.dto.EpisodeAccessResponse;
import com.shortvideo.backend.h5.dto.EpisodeResponse;
import com.shortvideo.backend.h5.dto.H5SnapshotResponse;
import com.shortvideo.backend.h5.dto.PayNodeResponse;
import com.shortvideo.backend.h5.dto.StorylineResponse;
import com.shortvideo.backend.h5.dto.UnlockOrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

@Service
public class H5Service {

    private static final String DEFAULT_DEVICE_ID = "demo-device";
    private static final BigDecimal DEFAULT_DRAW_AMOUNT = new BigDecimal("6.00");
    private static final DateTimeFormatter ORDER_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final H5UserService userService;
    private final String publicBaseUrl;
    private final boolean demoInstantUnlockEnabled;

    public H5Service(
            JdbcTemplate jdbc,
            H5UserService userService,
            @Value("${app.public-base-url}") String publicBaseUrl,
            @Value("${app.demo.instant-unlock-enabled:true}") boolean demoInstantUnlockEnabled
    ) {
        this.jdbc = jdbc;
        this.userService = userService;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.demoInstantUnlockEnabled = demoInstantUnlockEnabled;
    }

    public List<DramaResponse> listDramas() {
        return jdbc.query("""
                SELECT id, title, tag, episode_count, heat_text, cover_url
                FROM dramas
                WHERE status = 'PUBLISHED'
                ORDER BY sort_order, id
                """, (rs, rowNum) -> toDramaResponse(rs));
    }

    public DramaResponse getDefaultDrama() {
        return jdbc.query("""
                SELECT id, title, tag, episode_count, heat_text, cover_url
                FROM dramas
                WHERE status = 'PUBLISHED'
                ORDER BY sort_order, id
                LIMIT 1
                """, (rs, rowNum) -> toDramaResponse(rs)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No published dramas found"));
    }

    public DramaResponse getDrama(long dramaId) {
        return jdbc.query("""
                SELECT id, title, tag, episode_count, heat_text, cover_url
                FROM dramas
                WHERE id = ? AND status = 'PUBLISHED'
                """, (rs, rowNum) -> toDramaResponse(rs), dramaId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Drama not found: " + dramaId));
    }

    private DramaResponse toDramaResponse(ResultSet rs) throws SQLException {
        return new DramaResponse(
                rs.getLong("id"),
                repair(rs.getString("title")),
                repair(rs.getString("tag")),
                rs.getInt("episode_count"),
                repair(rs.getString("heat_text")),
                publicMediaUrl(rs.getString("cover_url"))
        );
    }

    public List<EpisodeResponse> listEpisodes(Long dramaId) {
        return listEpisodes(dramaId, null, null);
    }

    public List<EpisodeResponse> listEpisodes(Long dramaId, String deviceId) {
        return listEpisodes(dramaId, null, deviceId);
    }

    public List<EpisodeResponse> listEpisodes(Long dramaId, String authorization, String deviceId) {
        // H5 loads all episodes once, then filters by active drama in the client.
        // A dramaId is still supported for direct detail-page or admin checks.
        Set<String> unlockedStorylineIds = unlockedStorylineIds(authorization);
        if (dramaId == null) {
            return jdbc.query("""
                    SELECT id, drama_id, episode_no, title, storyline_id, cover_url, video_url
                    FROM episodes
                    WHERE status = 'PUBLISHED'
                    ORDER BY drama_id, storyline_id IS NOT NULL, storyline_id, episode_no
                    """, (rs, rowNum) -> new EpisodeResponse(
                    rs.getString("id"),
                    rs.getLong("drama_id"),
                    rs.getInt("episode_no"),
                    repair(rs.getString("title")),
                    rs.getString("storyline_id"),
                    publicMediaUrl(rs.getString("cover_url")),
                    playableVideoUrl(
                            rs.getString("video_url"),
                            rs.getString("storyline_id"),
                            unlockedStorylineIds
                    )
            ));
        }

        return jdbc.query("""
                SELECT id, drama_id, episode_no, title, storyline_id, cover_url, video_url
                FROM episodes
                WHERE drama_id = ? AND status = 'PUBLISHED'
                ORDER BY storyline_id IS NOT NULL, storyline_id, episode_no
                """, (rs, rowNum) -> new EpisodeResponse(
                rs.getString("id"),
                rs.getLong("drama_id"),
                rs.getInt("episode_no"),
                repair(rs.getString("title")),
                rs.getString("storyline_id"),
                publicMediaUrl(rs.getString("cover_url")),
                playableVideoUrl(
                        rs.getString("video_url"),
                        rs.getString("storyline_id"),
                        unlockedStorylineIds
                )
        ), dramaId);
    }

    public List<StorylineResponse> listStorylines(Long dramaId) {
        // Same contract as episodes: bulk load for the H5 shell, filtered load
        // for narrower API consumers.
        if (dramaId == null) {
            return jdbc.query("""
                    SELECT id, drama_id, name, rarity, description, cover_url
                    FROM storylines
                    WHERE status = 'ENABLED'
                    ORDER BY drama_id, sort_order, id
                    """, (rs, rowNum) -> new StorylineResponse(
                    rs.getString("id"),
                    rs.getLong("drama_id"),
                    repair(rs.getString("name")),
                    repair(rs.getString("rarity")),
                    repair(rs.getString("description")),
                    publicMediaUrl(rs.getString("cover_url"))
            ));
        }

        return jdbc.query("""
                SELECT id, drama_id, name, rarity, description, cover_url
                FROM storylines
                WHERE drama_id = ? AND status = 'ENABLED'
                ORDER BY sort_order, id
                """, (rs, rowNum) -> new StorylineResponse(
                rs.getString("id"),
                rs.getLong("drama_id"),
                repair(rs.getString("name")),
                repair(rs.getString("rarity")),
                repair(rs.getString("description")),
                publicMediaUrl(rs.getString("cover_url"))
        ), dramaId);
    }

    public H5SnapshotResponse snapshot() {
        return snapshot(null, null);
    }

    public H5SnapshotResponse snapshot(String deviceId) {
        return snapshot(null, deviceId);
    }

    public H5SnapshotResponse snapshot(String authorization, String deviceId) {
        return new H5SnapshotResponse(
                listDramas(),
                listEpisodes(null, authorization, deviceId),
                listStorylines(null)
        );
    }

    public EpisodeAccessResponse checkEpisodeAccess(EpisodeAccessRequest request) {
        return checkEpisodeAccess(request, null);
    }

    public EpisodeAccessResponse checkEpisodeAccess(EpisodeAccessRequest request, String authorization) {
        EpisodeForAccess nextEpisode = resolveEpisodeForAccess(request);
        if (nextEpisode.storylineId() == null || nextEpisode.storylineId().isBlank()) {
            return accessAllowed(nextEpisode);
        }

        boolean unlocked = userService.authenticatedUserId(authorization)
                .map((userId) -> hasUnlocked(userId, nextEpisode.storylineId()))
                .orElse(false);

        if (unlocked) {
            return accessAllowed(nextEpisode);
        }

        BigDecimal unlockAmount = resolveEpisodeUnlockAmount(nextEpisode);
        return new EpisodeAccessResponse(
                false,
                true,
                nextEpisode.id(),
                nextEpisode.number(),
                nextEpisode.storylineId(),
                new PayNodeResponse(
                        "random-storyline",
                        "解锁后续剧情",
                        "下一集进入付费分支，支付后将随机抽取 1 条后续支线并解锁该支线全部剧集。",
                        formatMoney(unlockAmount),
                        unlockAmount.setScale(2, RoundingMode.HALF_UP),
                        "CNY",
                        nextEpisode.id(),
                        nextEpisode.dramaId()
                )
        );
    }

    @Transactional
    public List<UnlockOrderResponse> listOrders(String deviceId) {
        return listOrders(null, deviceId);
    }

    @Transactional
    public List<UnlockOrderResponse> listOrders(String authorization, String deviceId) {
        long userId = userService.requestUserId(authorization, deviceId);
        return jdbc.query("""
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
                WHERE orders.user_id = ?
                ORDER BY orders.created_at DESC
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
        ), userId);
    }

    @Transactional
    public List<StorylineResponse> listUnlocks(String deviceId) {
        return listUnlocks(null, deviceId);
    }

    @Transactional
    public List<StorylineResponse> listUnlocks(String authorization, String deviceId) {
        long userId = userService.requestUserId(authorization, deviceId);
        return jdbc.query("""
                SELECT s.id, s.drama_id, s.name, s.rarity, s.description, s.cover_url
                FROM user_unlocks u
                JOIN storylines s ON s.id = u.storyline_id
                WHERE u.user_id = ?
                ORDER BY u.created_at DESC
                """, (rs, rowNum) -> new StorylineResponse(
                rs.getString("id"),
                rs.getLong("drama_id"),
                repair(rs.getString("name")),
                repair(rs.getString("rarity")),
                repair(rs.getString("description")),
                publicMediaUrl(rs.getString("cover_url"))
        ), userId);
    }

    @Transactional
    public DrawResponse drawStoryline(DrawRequest request) {
        return drawStoryline(request, null);
    }

    @Transactional
    public DrawResponse drawStoryline(DrawRequest request, String authorization) {
        if (!demoInstantUnlockEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo instant unlock is disabled");
        }

        long dramaId = resolveDramaId(request == null ? null : request.dramaId());
        long userId = userService.requestUserId(authorization, request == null ? null : request.deviceId());

        // Draw, order creation, and unlock creation must commit together. When
        // real payment is added, the paid callback should enter at this boundary.
        List<StorylineCandidate> candidates = jdbc.query("""
                SELECT s.id, s.drama_id, s.name, s.rarity, s.description, s.cover_url, s.weight
                FROM storylines s
                WHERE s.drama_id = ?
                  AND s.status = 'ENABLED'
                  AND NOT EXISTS (
                    SELECT 1 FROM user_unlocks u
                    WHERE u.user_id = ? AND u.storyline_id = s.id
                  )
                ORDER BY s.sort_order, s.id
                """, (rs, rowNum) -> new StorylineCandidate(
                new StorylineResponse(
                        rs.getString("id"),
                        rs.getLong("drama_id"),
                        rs.getString("name"),
                        rs.getString("rarity"),
                        rs.getString("description"),
                        publicMediaUrl(rs.getString("cover_url"))
                ),
                rs.getBigDecimal("weight")
        ), dramaId, userId);

        if (candidates.isEmpty()) {
            return new DrawResponse(null, null, "All storylines are already unlocked");
        }

        StorylineResponse line = selectWeighted(candidates).line();
        BigDecimal amount = resolveDrawAmount(request);
        chargeBalance(userId, amount);
        String methodKey = defaultText(request == null ? null : request.methodKey(), "balance");
        String methodName = defaultText(request == null ? null : request.methodName(), "Demo balance");
        String orderId = nextOrderId();

        jdbc.update("""
                INSERT INTO orders
                (id, user_id, drama_id, storyline_id, title, amount, payment_method, payment_method_key, status, paid_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PAID', CURRENT_TIMESTAMP)
                """, orderId, userId, dramaId, line.id(), line.name(), amount, methodName, methodKey);
        jdbc.update("""
                INSERT INTO user_unlocks (user_id, drama_id, storyline_id, order_id)
                VALUES (?, ?, ?, ?)
                """, userId, dramaId, line.id(), orderId);

        UnlockOrderResponse order = new UnlockOrderResponse(
                orderId,
                line.name(),
                formatMoney(amount),
                methodName,
                methodKey,
                LocalDateTime.now().format(DISPLAY_TIME),
                "PAID",
                "",
                ""
        );
        return new DrawResponse(line, order, "Unlocked: " + line.name());
    }

    private void chargeBalance(long userId, BigDecimal amount) {
        BigDecimal payable = amount == null ? DEFAULT_DRAW_AMOUNT : amount.setScale(2, RoundingMode.HALF_UP);
        int updated = jdbc.update("""
                UPDATE app_users
                SET balance = balance - ?,
                    paid_amount = paid_amount + ?
                WHERE id = ? AND balance >= ?
                """, payable, payable, userId, payable);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Insufficient balance");
        }
    }

    private long resolveDramaId(Long dramaId) {
        if (dramaId != null) {
            return dramaId;
        }
        return jdbc.query("""
                SELECT id
                FROM dramas
                WHERE status = 'PUBLISHED'
                ORDER BY sort_order, id
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id")).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No published dramas found"));
    }

    private long requireActiveUser(long userId) {
        String status = jdbc.query("SELECT status FROM app_users WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"), userId).stream().findFirst().orElse("NORMAL");
        if ("FROZEN".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is frozen");
        }
        return userId;
    }

    private long findOrCreateUser(String deviceId) {
        jdbc.update("""
                INSERT IGNORE INTO app_users (device_id, nickname)
                VALUES (?, ?)
                """, deviceId, "Guest " + Math.abs(deviceId.hashCode() % 10000));
        return findUserId(deviceId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User creation failed"));
    }

    private List<Long> findUserId(String deviceId) {
        return jdbc.query("SELECT id FROM app_users WHERE device_id = ?",
                (rs, rowNum) -> rs.getLong("id"), deviceId);
    }

    private EpisodeAccessResponse accessAllowed(EpisodeForAccess episode) {
        return new EpisodeAccessResponse(
                true,
                false,
                episode.id(),
                episode.number(),
                episode.storylineId(),
                null
        );
    }

    private EpisodeForAccess resolveEpisodeForAccess(EpisodeAccessRequest request) {
        if (request == null) {
            return new EpisodeForAccess("", null, 0, null, null);
        }

        if (request.nextEpisodeId() != null && !request.nextEpisodeId().isBlank()) {
            List<EpisodeForAccess> matches = jdbc.query("""
                    SELECT id, drama_id, episode_no, storyline_id, unlock_price
                    FROM episodes
                    WHERE id = ? AND status = 'PUBLISHED'
                    """, (rs, rowNum) -> new EpisodeForAccess(
                    rs.getString("id"),
                    rs.getLong("drama_id"),
                    rs.getInt("episode_no"),
                    rs.getString("storyline_id"),
                    rs.getBigDecimal("unlock_price")
            ), request.nextEpisodeId());
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
        }

        return new EpisodeForAccess(
                defaultText(request.nextEpisodeId(), ""),
                request.dramaId(),
                request.nextEpisodeNumber() == null ? 0 : request.nextEpisodeNumber(),
                request.storylineId(),
                null
        );
    }

    private boolean hasUnlocked(long userId, String storylineId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM user_unlocks
                WHERE user_id = ? AND storyline_id = ?
                """, Integer.class, userId, storylineId);
        return count != null && count > 0;
    }

    private BigDecimal findStorylineAmount(String storylineId) {
        return jdbc.query("""
                SELECT p.draw_price
                FROM storylines s
                JOIN story_pools p ON p.id = s.pool_id
                WHERE s.id = ?
                """, (rs, rowNum) -> rs.getBigDecimal("draw_price"), storylineId)
                .stream()
                .findFirst()
                .orElse(DEFAULT_DRAW_AMOUNT);
    }

    private BigDecimal resolveDrawAmount(DrawRequest request) {
        BigDecimal fallback = parseAmount(request == null ? null : request.amount());
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
                .map((amount) -> amount.setScale(2, RoundingMode.HALF_UP))
                .orElse(fallback);
    }

    private BigDecimal resolveEpisodeUnlockAmount(EpisodeForAccess episode) {
        if (episode.unlockPrice() != null) {
            return episode.unlockPrice();
        }
        if (episode.id() != null && !episode.id().isBlank()) {
            return jdbc.query("""
                    SELECT unlock_price
                    FROM episodes
                    WHERE id = ? AND unlock_price IS NOT NULL
                    """, (rs, rowNum) -> rs.getBigDecimal("unlock_price"), episode.id())
                    .stream()
                    .findFirst()
                    .orElseGet(() -> fallbackUnlockAmount(episode.storylineId()));
        }
        return fallbackUnlockAmount(episode.storylineId());
    }

    private BigDecimal fallbackUnlockAmount(String storylineId) {
        if (storylineId == null || storylineId.isBlank()) {
            return DEFAULT_DRAW_AMOUNT;
        }
        return findStorylineAmount(storylineId);
    }

    private Set<String> unlockedStorylineIds(String authorization) {
        return userService.authenticatedUserId(authorization)
                .map(this::unlockedStorylineIdsByUserId)
                .orElse(Set.of());
    }

    private Set<String> unlockedStorylineIdsByUserId(long userId) {
        return new HashSet<>(jdbc.query("""
                SELECT storyline_id
                FROM user_unlocks
                WHERE user_id = ?
                """, (rs, rowNum) -> rs.getString("storyline_id"), userId));
    }

    private String playableVideoUrl(String videoUrl, String storylineId, Set<String> unlockedStorylineIds) {
        if (storylineId == null || storylineId.isBlank() || unlockedStorylineIds.contains(storylineId)) {
            return publicMediaUrl(videoUrl);
        }
        return "";
    }

    private String publicMediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        String text = url.trim();
        if (text.startsWith("/")) {
            return publicBaseUrl + text;
        }

        try {
            URI uri = URI.create(text);
            if (uri.getScheme() == null) {
                return publicBaseUrl + "/" + text;
            }
            String host = uri.getHost();
            int port = uri.getPort();
            if (("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) && (port == 8080 || port == 8081)) {
                String path = uri.getRawPath() == null ? "" : uri.getRawPath();
                String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
                return publicBaseUrl + path + query;
            }
        } catch (IllegalArgumentException ignored) {
            return text;
        }
        return text;
    }

    private String trimTrailingSlash(String value) {
        String text = defaultText(value, "http://localhost:8081");
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private StorylineCandidate selectWeighted(List<StorylineCandidate> candidates) {
        double total = candidates.stream()
                .map(StorylineCandidate::weight)
                .mapToDouble(weight -> weight == null ? 0 : weight.doubleValue())
                .sum();
        if (total <= 0) {
            return candidates.get(0);
        }

        double cursor = ThreadLocalRandom.current().nextDouble(total);
        for (StorylineCandidate candidate : candidates) {
            cursor -= candidate.weight() == null ? 0 : candidate.weight().doubleValue();
            if (cursor <= 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return DEFAULT_DEVICE_ID;
        }
        return deviceId.trim();
    }

    private BigDecimal parseAmount(String amountText) {
        if (amountText == null || amountText.isBlank()) {
            return DEFAULT_DRAW_AMOUNT;
        }
        // Frontend amounts are display strings, so tolerate currency symbols.
        String cleaned = amountText.replaceAll("[^0-9.]", "").trim();
        if (cleaned.isBlank()) {
            return DEFAULT_DRAW_AMOUNT;
        }
        try {
            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return DEFAULT_DRAW_AMOUNT;
        }
    }

    private String formatMoney(BigDecimal amount) {
        return "\u00a5" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String nextOrderId() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "SL" + LocalDateTime.now().format(ORDER_ID_TIME) + suffix;
    }

    private record StorylineCandidate(StorylineResponse line, BigDecimal weight) {
    }

    private record EpisodeForAccess(String id, Long dramaId, Integer number, String storylineId, BigDecimal unlockPrice) {
    }
}
