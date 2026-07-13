package com.shortvideo.backend.h5;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class H5Service {

    private static final String DEFAULT_DEVICE_ID = "demo-device";
    private static final BigDecimal DEFAULT_DRAW_AMOUNT = new BigDecimal("6.00");
    private static final DateTimeFormatter ORDER_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;

    public H5Service(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DramaResponse> listDramas() {
        return jdbc.query("""
                SELECT id, title, tag, episode_count, heat_text, cover_url
                FROM dramas
                WHERE status = 'PUBLISHED'
                ORDER BY sort_order, id
                """, (rs, rowNum) -> new DramaResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("tag"),
                rs.getInt("episode_count"),
                rs.getString("heat_text"),
                rs.getString("cover_url")
        ));
    }

    public DramaResponse getDefaultDrama() {
        return listDramas().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No published dramas found"));
    }

    public DramaResponse getDrama(long dramaId) {
        return jdbc.query("""
                SELECT id, title, tag, episode_count, heat_text, cover_url
                FROM dramas
                WHERE id = ? AND status = 'PUBLISHED'
                """, (rs, rowNum) -> new DramaResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("tag"),
                rs.getInt("episode_count"),
                rs.getString("heat_text"),
                rs.getString("cover_url")
        ), dramaId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Drama not found: " + dramaId));
    }

    public List<EpisodeResponse> listEpisodes(Long dramaId) {
        return listEpisodes(dramaId, null);
    }

    public List<EpisodeResponse> listEpisodes(Long dramaId, String deviceId) {
        // H5 loads all episodes once, then filters by active drama in the client.
        // A dramaId is still supported for direct detail-page or admin checks.
        Set<String> unlockedStorylineIds = unlockedStorylineIds(deviceId);
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
                    rs.getString("title"),
                    rs.getString("storyline_id"),
                    rs.getString("cover_url"),
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
                rs.getString("title"),
                rs.getString("storyline_id"),
                rs.getString("cover_url"),
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
                    rs.getString("name"),
                    rs.getString("rarity"),
                    rs.getString("description"),
                    rs.getString("cover_url")
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
                rs.getString("name"),
                rs.getString("rarity"),
                rs.getString("description"),
                rs.getString("cover_url")
        ), dramaId);
    }

    public H5SnapshotResponse snapshot() {
        return snapshot(null);
    }

    public H5SnapshotResponse snapshot(String deviceId) {
        return new H5SnapshotResponse(
                listDramas(),
                listEpisodes(null, deviceId),
                listStorylines(null)
        );
    }

    public EpisodeAccessResponse checkEpisodeAccess(EpisodeAccessRequest request) {
        EpisodeForAccess nextEpisode = resolveEpisodeForAccess(request);
        if (nextEpisode.storylineId() == null || nextEpisode.storylineId().isBlank()) {
            return accessAllowed(nextEpisode);
        }

        String deviceId = normalizeDeviceId(request == null ? null : request.deviceId());
        boolean unlocked = findUserId(deviceId).stream()
                .findFirst()
                .map(userId -> hasUnlocked(userId, nextEpisode.storylineId()))
                .orElse(false);

        if (unlocked) {
            return accessAllowed(nextEpisode);
        }

        return new EpisodeAccessResponse(
                false,
                true,
                nextEpisode.id(),
                nextEpisode.number(),
                nextEpisode.storylineId(),
                new PayNodeResponse(
                        "random-storyline",
                        "Unlock next storyline",
                        "This episode belongs to a locked storyline. Unlock one random storyline to continue.",
                        formatMoney(findStorylineAmount(nextEpisode.storylineId()))
                )
        );
    }

    public List<UnlockOrderResponse> listOrders(String deviceId) {
        List<Long> userIds = findUserId(normalizeDeviceId(deviceId));
        if (userIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query("""
                SELECT id, title, amount, payment_method, payment_method_key,
                       DATE_FORMAT(COALESCE(paid_at, created_at), '%m-%d %H:%i') AS time_text
                FROM orders
                WHERE user_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> new UnlockOrderResponse(
                rs.getString("id"),
                rs.getString("title"),
                formatMoney(rs.getBigDecimal("amount")),
                rs.getString("payment_method"),
                rs.getString("payment_method_key"),
                rs.getString("time_text")
        ), userIds.get(0));
    }

    public List<StorylineResponse> listUnlocks(String deviceId) {
        List<Long> userIds = findUserId(normalizeDeviceId(deviceId));
        if (userIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query("""
                SELECT s.id, s.drama_id, s.name, s.rarity, s.description, s.cover_url
                FROM user_unlocks u
                JOIN storylines s ON s.id = u.storyline_id
                WHERE u.user_id = ?
                ORDER BY u.created_at DESC
                """, (rs, rowNum) -> new StorylineResponse(
                rs.getString("id"),
                rs.getLong("drama_id"),
                rs.getString("name"),
                rs.getString("rarity"),
                rs.getString("description"),
                rs.getString("cover_url")
        ), userIds.get(0));
    }

    @Transactional
    public DrawResponse drawStoryline(DrawRequest request) {
        long dramaId = resolveDramaId(request == null ? null : request.dramaId());
        long userId = requireActiveUser(findOrCreateUser(normalizeDeviceId(request == null ? null : request.deviceId())));

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
                        rs.getString("cover_url")
                ),
                rs.getBigDecimal("weight")
        ), dramaId, userId);

        if (candidates.isEmpty()) {
            return new DrawResponse(null, null, "All storylines are already unlocked");
        }

        StorylineResponse line = selectWeighted(candidates).line();
        BigDecimal amount = parseAmount(request == null ? null : request.amount());
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
                LocalDateTime.now().format(DISPLAY_TIME)
        );
        return new DrawResponse(line, order, "Unlocked: " + line.name());
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
            return new EpisodeForAccess("", null, 0, null);
        }

        if (request.nextEpisodeId() != null && !request.nextEpisodeId().isBlank()) {
            List<EpisodeForAccess> matches = jdbc.query("""
                    SELECT id, drama_id, episode_no, storyline_id
                    FROM episodes
                    WHERE id = ? AND status = 'PUBLISHED'
                    """, (rs, rowNum) -> new EpisodeForAccess(
                    rs.getString("id"),
                    rs.getLong("drama_id"),
                    rs.getInt("episode_no"),
                    rs.getString("storyline_id")
            ), request.nextEpisodeId());
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
        }

        return new EpisodeForAccess(
                defaultText(request.nextEpisodeId(), ""),
                request.dramaId(),
                request.nextEpisodeNumber() == null ? 0 : request.nextEpisodeNumber(),
                request.storylineId()
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

    private Set<String> unlockedStorylineIds(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Set.of();
        }

        List<Long> userIds = findUserId(normalizeDeviceId(deviceId));
        if (userIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(jdbc.query("""
                SELECT storyline_id
                FROM user_unlocks
                WHERE user_id = ?
                """, (rs, rowNum) -> rs.getString("storyline_id"), userIds.get(0)));
    }

    private String playableVideoUrl(String videoUrl, String storylineId, Set<String> unlockedStorylineIds) {
        if (storylineId == null || storylineId.isBlank() || unlockedStorylineIds.contains(storylineId)) {
            return videoUrl;
        }
        return "";
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

    private record EpisodeForAccess(String id, Long dramaId, Integer number, String storylineId) {
    }
}
