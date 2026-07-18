package com.shortvideo.backend.admin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.shortvideo.backend.admin.dto.H5DramaPayload;
import com.shortvideo.backend.admin.dto.H5EpisodePayload;
import com.shortvideo.backend.admin.dto.H5SnapshotPayload;
import com.shortvideo.backend.admin.dto.H5StorylinePayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

@Service
public class H5CatalogSyncService {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final JdbcTemplate jdbc;

    public H5CatalogSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void sync(H5SnapshotPayload h5Payload) {
        if (h5Payload == null || h5Payload.dramas().isEmpty()) {
            return;
        }

        // The admin UI still saves one snapshot. Its h5 node is now parsed into
        // DTOs before this service projects it into runtime tables.
        Map<String, Long> dramaIds = syncDramas(h5Payload.dramas());
        Set<String> storylineIds = syncStorylines(h5Payload.storylines(), dramaIds);
        syncEpisodes(h5Payload.episodes(), dramaIds, storylineIds);
    }

    private Map<String, Long> syncDramas(List<H5DramaPayload> dramas) {
        Map<String, Long> dramaIds = new LinkedHashMap<>();
        Set<Long> activeIds = new LinkedHashSet<>();

        for (int index = 0; index < dramas.size(); index++) {
            H5DramaPayload item = dramas.get(index);
            String sourceId = text(item.id(), String.valueOf(index + 1));
            // Admin IDs are string values such as D001; the current schema keeps
            // drama IDs numeric for simpler joins and mobile payloads.
            long id = numericId(sourceId, index + 1);
            dramaIds.put(sourceId, id);
            activeIds.add(id);

            jdbc.update("""
                    INSERT INTO dramas
                    (id, title, tag, episode_count, heat_text, cover_url, status, play_count, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      title = VALUES(title),
                      tag = VALUES(tag),
                      episode_count = VALUES(episode_count),
                      heat_text = VALUES(heat_text),
                      cover_url = VALUES(cover_url),
                      status = VALUES(status),
                      play_count = VALUES(play_count),
                      sort_order = VALUES(sort_order)
                    """,
                    id,
                    limit(text(item.title(), "Untitled drama"), 128),
                    limit(text(item.tag(), "Drama"), 64),
                    intValue(item.episodes(), 0),
                    limit(text(item.heat(), "0"), 32),
                    limit(text(item.cover(), ""), 512),
                    isActive(item.status()) ? "PUBLISHED" : "ARCHIVED",
                    longValue(item.playCount(), 0),
                    index
            );
        }

        // Missing rows are archived rather than deleted so historical orders and
        // unlock records keep their foreign-key targets.
        markMissingLong("dramas", "id", "status", "ARCHIVED", activeIds);
        return dramaIds;
    }

    private Set<String> syncStorylines(List<H5StorylinePayload> storylines, Map<String, Long> dramaIds) {
        Set<String> activeStorylineIds = new LinkedHashSet<>();
        Set<String> activePoolIds = new LinkedHashSet<>();
        Long defaultDramaId = dramaIds.values().stream().findFirst().orElse(null);

        if (defaultDramaId == null) {
            return activeStorylineIds;
        }

        for (int index = 0; index < storylines.size(); index++) {
            H5StorylinePayload item = storylines.get(index);
            String id = limit(text(item.id(), "storyline-" + (index + 1)), 64);
            // The current admin H5 payload models one story pool as one drawable
            // storyline. Later CRUD APIs can split pool and line management.
            String poolId = limit(text(item.poolId(), id), 64);
            long dramaId = resolveDramaId(item.dramaId(), dramaIds, defaultDramaId);
            String status = isActive(item.status()) ? "ENABLED" : "DISABLED";
            String name = limit(text(item.name(), "Storyline"), 128);

            activePoolIds.add(poolId);
            activeStorylineIds.add(id);

            jdbc.update("""
                    INSERT INTO story_pools (id, drama_id, name, status, draw_price)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      drama_id = VALUES(drama_id),
                      name = VALUES(name),
                      status = VALUES(status),
                      draw_price = VALUES(draw_price)
                    """,
                    poolId,
                    dramaId,
                    name,
                    status,
                    money(item.price(), new BigDecimal("6.00"))
            );

            jdbc.update("""
                    INSERT INTO storylines
                    (id, drama_id, pool_id, name, rarity, description, cover_url, weight, status, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      drama_id = VALUES(drama_id),
                      pool_id = VALUES(pool_id),
                      name = VALUES(name),
                      rarity = VALUES(rarity),
                      description = VALUES(description),
                      cover_url = VALUES(cover_url),
                      weight = VALUES(weight),
                      status = VALUES(status),
                      sort_order = VALUES(sort_order)
                    """,
                    id,
                    dramaId,
                    poolId,
                    name,
                    limit(text(item.rarity(), "R"), 16),
                    limit(text(item.desc(), ""), 512),
                    limit(text(item.cover(), ""), 512),
                    weightByRarity(item.rarity()),
                    status,
                    index
            );
        }

        if (!activePoolIds.isEmpty()) {
            markMissingString("story_pools", "id", "status", "DISABLED", activePoolIds);
        }
        if (!activeStorylineIds.isEmpty()) {
            markMissingString("storylines", "id", "status", "DISABLED", activeStorylineIds);
        }
        return activeStorylineIds;
    }

    private void syncEpisodes(List<H5EpisodePayload> episodes, Map<String, Long> dramaIds, Set<String> storylineIds) {
        Set<String> activeEpisodeIds = new LinkedHashSet<>();
        Long defaultDramaId = dramaIds.values().stream().findFirst().orElse(null);

        if (defaultDramaId == null) {
            return;
        }

        for (int index = 0; index < episodes.size(); index++) {
            H5EpisodePayload item = episodes.get(index);
            long dramaId = resolveDramaId(item.dramaId(), dramaIds, defaultDramaId);
            int episodeNo = intValue(item.number(), index + 1);
            String id = limit(text(item.id(), dramaId + "-main-" + episodeNo), 64);
            String storylineId = text(item.storylineId(), "");
            // Keep imported episodes playable even if their old storyline was
            // removed from the current pool snapshot.
            if (storylineId.isBlank() || !storylineIds.contains(storylineId)) {
                storylineId = null;
            }
            activeEpisodeIds.add(id);

            jdbc.update("""
                    INSERT INTO episodes
                    (id, drama_id, episode_no, title, storyline_id, cover_url, video_url, status, pay_node)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      drama_id = VALUES(drama_id),
                      episode_no = VALUES(episode_no),
                      title = VALUES(title),
                      storyline_id = VALUES(storyline_id),
                      cover_url = VALUES(cover_url),
                      video_url = VALUES(video_url),
                      status = VALUES(status),
                      pay_node = VALUES(pay_node)
                    """,
                    id,
                    dramaId,
                    episodeNo,
                    limit(text(item.title(), "Episode " + episodeNo), 128),
                    storylineId,
                    limit(text(item.cover(), ""), 512),
                    limit(text(item.videoUrl(), ""), 512),
                    isActive(item.status()) ? "PUBLISHED" : "ARCHIVED",
                    bool(item.isPayNode())
            );
        }

        if (!activeEpisodeIds.isEmpty()) {
            markMissingString("episodes", "id", "status", "ARCHIVED", activeEpisodeIds);
        }
    }

    private long resolveDramaId(Object value, Map<String, Long> dramaIds, long fallback) {
        String sourceId = text(value, "");
        if (dramaIds.containsKey(sourceId)) {
            return dramaIds.get(sourceId);
        }
        return numericId(sourceId, fallback);
    }

    private long numericId(String value, long fallback) {
        Matcher matcher = DIGITS.matcher(value == null ? "" : value);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Long.parseLong(matcher.group());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value, ""));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(text(value, ""));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal money(Object value, BigDecimal fallback) {
        String cleaned = text(value, "").replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal weightByRarity(Object value) {
        // Rarer lines receive lower random weights. Replace this rule with
        // explicit pool weights when the admin screen supports per-line editing.
        String rarity = text(value, "R").toUpperCase(Locale.ROOT);
        if ("SSR".equals(rarity)) {
            return new BigDecimal("0.250");
        }
        if ("SR".equals(rarity)) {
            return new BigDecimal("0.500");
        }
        return new BigDecimal("1.000");
    }

    private boolean isActive(Object value) {
        String status = text(value, "");
        if (status.isBlank()) {
            return true;
        }

        String normalized = status.toLowerCase(Locale.ROOT);
        return !(normalized.contains("draft")
                || normalized.contains("off")
                || normalized.contains("disabled")
                || normalized.contains("archive")
                || status.contains("下架")
                || status.contains("暂停")
                || status.contains("停用"));
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(text(value, "false"));
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : repair(text);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void markMissingLong(String table, String idColumn, String statusColumn, String statusValue, Set<Long> ids) {
        // Table and column names are hard-coded by callers; only values are
        // parameterized here.
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(statusValue);
        params.addAll(ids);
        jdbc.update("UPDATE " + table + " SET " + statusColumn + " = ? WHERE " + idColumn + " NOT IN (" + placeholders + ")",
                params.toArray());
    }

    private void markMissingString(String table, String idColumn, String statusColumn, String statusValue, Set<String> ids) {
        // Table and column names are hard-coded by callers; only values are
        // parameterized here.
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(statusValue);
        params.addAll(ids);
        jdbc.update("UPDATE " + table + " SET " + statusColumn + " = ? WHERE " + idColumn + " NOT IN (" + placeholders + ")",
                params.toArray());
    }
}
