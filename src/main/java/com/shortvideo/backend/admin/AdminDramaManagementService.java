package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.shortvideo.backend.admin.dto.AdminContentReviewLogResponse;
import com.shortvideo.backend.admin.dto.AdminDramaRequest;
import com.shortvideo.backend.admin.dto.AdminDramaResponse;
import com.shortvideo.backend.admin.dto.AdminEpisodeRequest;
import com.shortvideo.backend.security.AppPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDramaManagementService {

    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String DEFAULT_COVER =
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=720&q=82";
    private static final String DEFAULT_VIDEO = "https://samplelib.com/mp4/sample-10s-360p.mp4";

    private final JdbcTemplate jdbc;
    private final AdminAuditService auditService;

    public AdminDramaManagementService(JdbcTemplate jdbc, AdminAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<AdminDramaResponse> listDramas(String keyword) {
        String search = safe(keyword).toLowerCase(Locale.ROOT);
        String like = "%" + search + "%";
        return jdbc.query("""
                SELECT d.id, d.title, d.tag, d.episode_count, d.cover_url, d.status,
                       d.owner, d.review_note, d.play_count, d.updated_at,
                       (SELECT COUNT(*) FROM episodes e
                        WHERE e.drama_id = d.id AND e.status <> 'ARCHIVED') AS uploaded,
                       (SELECT COUNT(*) FROM episodes e
                        WHERE e.drama_id = d.id AND e.pay_node = TRUE AND e.status <> 'ARCHIVED') AS pay_nodes,
                       (SELECT COUNT(*) FROM orders o
                        WHERE o.drama_id = d.id AND (UPPER(o.status) = 'PAID' OR o.status = '已支付')) AS paid_orders,
                       (SELECT COALESCE(SUM(o.amount), 0) FROM orders o
                        WHERE o.drama_id = d.id AND (UPPER(o.status) = 'PAID' OR o.status = '已支付')) AS revenue,
                       COALESCE(
                         (SELECT MIN(e.unlock_price) FROM episodes e
                          WHERE e.drama_id = d.id AND e.unlock_price IS NOT NULL AND e.status <> 'ARCHIVED'),
                         (SELECT MIN(p.draw_price) FROM story_pools p WHERE p.drama_id = d.id),
                         0
                       ) AS price
                FROM dramas d
                WHERE ? = ''
                   OR LOWER(d.title) LIKE ?
                   OR LOWER(d.tag) LIKE ?
                ORDER BY d.sort_order, d.id
                """, (rs, rowNum) -> toDramaResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("tag"),
                rs.getInt("episode_count"),
                rs.getString("cover_url"),
                rs.getString("status"),
                rs.getString("owner"),
                rs.getString("review_note"),
                rs.getLong("play_count"),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                rs.getInt("uploaded"),
                rs.getInt("pay_nodes"),
                rs.getInt("paid_orders"),
                money(rs.getBigDecimal("revenue")),
                money(rs.getBigDecimal("price"))
        ), search, like, like);
    }

    public AdminDramaResponse getDrama(String id) {
        long dramaId = numericDramaId(id);
        return listDramas("").stream()
                .filter((drama) -> drama.rawId().equals(dramaId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drama not found: " + id));
    }

    @Transactional
    public AdminDramaResponse createDrama(AdminDramaRequest request) {
        String title = text(request == null ? null : request.title(), "未命名短剧");
        String category = text(request == null ? null : request.category(), "未分类");
        int episodeCount = positive(request == null ? null : request.episodes(), 24);
        String cover = text(request == null ? null : request.cover(), DEFAULT_COVER);
        String status = toInternalDramaStatus(request == null ? null : request.status(), "DRAFT");
        String owner = text(request == null ? null : request.owner(), "内容运营");
        String reviewNote = text(request == null ? null : request.reviewNote(), defaultDramaReviewNote(status));
        BigDecimal price = parseMoney(request == null ? null : request.price(), new BigDecimal("6.00"));
        long id = nextDramaId();
        int sortOrder = nextDramaSortOrder();

        jdbc.update("""
                INSERT INTO dramas
                (id, title, tag, episode_count, heat_text, cover_url, status, owner, review_note, play_count, sort_order)
                VALUES (?, ?, ?, ?, '0', ?, ?, ?, ?, 0, ?)
                """, id, title, category, episodeCount, cover, status, owner, reviewNote, sortOrder);

        jdbc.update("""
                INSERT INTO story_pools (id, drama_id, name, status, draw_price)
                VALUES (?, ?, ?, 'ENABLED', ?)
                """, "pool-" + id, id, title + "故事线池", price);

        auditService.record("DRAMA_CREATE", "drama", String.valueOf(id), Map.of(
                "title", title,
                "status", status,
                "price", price.toPlainString()
        ));
        recordContentReview("DRAMA", String.valueOf(id), id, title, status, reviewNote);
        return getDrama(String.valueOf(id));
    }

    @Transactional
    public AdminDramaResponse updateDrama(String id, AdminDramaRequest request) {
        long dramaId = numericDramaId(id);
        AdminDramaResponse current = getDrama(id);
        String title = text(request == null ? null : request.title(), current.title());
        String category = text(request == null ? null : request.category(), current.category());
        int episodeCount = positive(request == null ? null : request.episodes(), current.episodes());
        String cover = text(request == null ? null : request.cover(), current.cover());
        String status = toInternalDramaStatus(request == null ? null : request.status(), toInternalDramaStatus(current.status(), "DRAFT"));
        String owner = text(request == null ? null : request.owner(), current.owner());
        String reviewNote = request != null && request.reviewNote() != null
                ? safe(request.reviewNote())
                : current.reviewNote();
        BigDecimal price = parseMoney(request == null ? null : request.price(), parseMoney(current.price(), BigDecimal.ZERO));

        int updated = jdbc.update("""
                UPDATE dramas
                SET title = ?, tag = ?, episode_count = ?, cover_url = ?, status = ?, owner = ?, review_note = ?
                WHERE id = ?
                """, title, category, episodeCount, cover, status, owner, reviewNote, dramaId);
        ensureUpdated(updated, "Drama not found: " + id);

        if (price.compareTo(BigDecimal.ZERO) > 0) {
            jdbc.update("UPDATE story_pools SET draw_price = ? WHERE drama_id = ?", price, dramaId);
            jdbc.update("""
                    UPDATE episodes
                    SET unlock_price = ?
                    WHERE drama_id = ? AND pay_node = TRUE AND status <> 'ARCHIVED'
                    """, price, dramaId);
        }

        auditService.record("DRAMA_UPDATE", "drama", String.valueOf(dramaId), Map.of(
                "title", title,
                "status", status,
                "price", price.toPlainString()
        ));
        recordContentReview("DRAMA", String.valueOf(dramaId), dramaId, title, status, reviewNote);
        return getDrama(id);
    }

    @Transactional
    public AdminDramaResponse updateDramaStatus(String id, String status, String reviewNote) {
        long dramaId = numericDramaId(id);
        AdminDramaResponse current = getDrama(id);
        String internalStatus = toInternalDramaStatus(status, "DRAFT");
        String note = text(reviewNote, defaultDramaReviewNote(internalStatus));
        int updated = jdbc.update(
                "UPDATE dramas SET status = ?, review_note = ? WHERE id = ?",
                internalStatus,
                note,
                dramaId
        );
        ensureUpdated(updated, "Drama not found: " + id);
        auditService.record("DRAMA_STATUS_UPDATE", "drama", String.valueOf(dramaId), Map.of(
                "status", internalStatus,
                "note", note
        ));
        recordContentReview("DRAMA", String.valueOf(dramaId), dramaId, current.title(), internalStatus, note);
        return getDrama(id);
    }

    @Transactional
    public AdminDramaResponse createEpisode(String dramaIdText, AdminEpisodeRequest request) {
        long dramaId = numericDramaId(dramaIdText);
        AdminDramaResponse drama = getDrama(dramaIdText);
        int episodeNo = request != null && request.index() != null
                ? Math.max(1, request.index())
                : nextEpisodeNumber(dramaId);
        String title = text(request == null ? null : request.title(), "第 " + episodeNo + " 集");
        String episodeId = uniqueEpisodeId(
                text(request == null ? null : request.id(), "d" + dramaId + "-main-" + episodeNo)
        );
        String cover = text(request == null ? null : request.cover(), drama.cover());
        String videoUrl = text(request == null ? null : request.videoUrl(), DEFAULT_VIDEO);
        String duration = text(request == null ? null : request.duration(), "01:30");
        String status = toInternalEpisodeStatus(request == null ? null : request.status(), "DRAFT");
        String reviewNote = text(request == null ? null : request.reviewNote(), defaultEpisodeReviewNote(status));
        boolean payNode = request != null && Boolean.TRUE.equals(request.payNode());
        BigDecimal unlockPrice = resolveEpisodePrice(request, drama.price(), payNode);
        String storylineId = nullable(request == null ? null : request.storylineId());

        jdbc.update("""
                INSERT INTO episodes
                (id, drama_id, episode_no, title, duration, storyline_id, cover_url, video_url, status, review_note, pay_node, unlock_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, episodeId, dramaId, episodeNo, title, duration, storylineId, cover, videoUrl, status, reviewNote, payNode, unlockPrice);

        jdbc.update("""
                UPDATE dramas
                SET episode_count = GREATEST(episode_count, ?)
                WHERE id = ?
                """, episodeNo, dramaId);
        auditService.record("EPISODE_CREATE", "episode", episodeId, Map.of(
                "dramaId", String.valueOf(dramaId),
                "episodeNo", String.valueOf(episodeNo),
                "status", status
        ));
        recordContentReview("EPISODE", episodeId, dramaId, title, status, reviewNote);
        return getDrama(dramaIdText);
    }

    @Transactional
    public AdminDramaResponse updateEpisode(String dramaIdText, String episodeId, AdminEpisodeRequest request) {
        long dramaId = numericDramaId(dramaIdText);
        AdminDramaResponse.EpisodeItem current = episode(dramaId, episodeId);
        int episodeNo = request != null && request.index() != null ? Math.max(1, request.index()) : current.index();
        String title = text(request == null ? null : request.title(), current.title());
        String status = toInternalEpisodeStatus(
                request == null ? null : request.status(),
                toInternalEpisodeStatus(current.status(), "DRAFT")
        );
        boolean payNode = request != null && request.payNode() != null ? request.payNode() : current.payNode();
        String duration = text(request == null ? null : request.duration(), current.duration());
        String cover = text(request == null ? null : request.cover(), current.cover());
        String videoUrl = text(request == null ? null : request.videoUrl(), current.videoUrl());
        String storylineId = request != null && request.storylineId() != null
                ? nullable(request.storylineId())
                : current.storylineId();
        String reviewNote = request != null && request.reviewNote() != null
                ? safe(request.reviewNote())
                : current.reviewNote();
        BigDecimal unlockPrice = resolveEpisodePrice(request, current.price(), payNode);

        int updated = jdbc.update("""
                UPDATE episodes
                SET episode_no = ?, title = ?, duration = ?, status = ?, review_note = ?, pay_node = ?,
                    cover_url = ?, video_url = ?, storyline_id = ?, unlock_price = ?
                WHERE id = ? AND drama_id = ?
                """, episodeNo, title, duration, status, reviewNote, payNode, cover, videoUrl, storylineId, unlockPrice, episodeId, dramaId);
        ensureUpdated(updated, "Episode not found: " + episodeId);

        jdbc.update("""
                UPDATE dramas
                SET episode_count = GREATEST(
                    episode_count,
                    COALESCE((SELECT MAX(e.episode_no) FROM episodes e
                              WHERE e.drama_id = ? AND e.status <> 'ARCHIVED'), 0)
                )
                WHERE id = ?
                """, dramaId, dramaId);
        auditService.record("EPISODE_UPDATE", "episode", episodeId, Map.of(
                "dramaId", String.valueOf(dramaId),
                "episodeNo", String.valueOf(episodeNo),
                "status", status
        ));
        recordContentReview("EPISODE", episodeId, dramaId, title, status, reviewNote);
        return getDrama(dramaIdText);
    }

    @Transactional
    public AdminDramaResponse batchImportEpisodes(String dramaIdText, List<AdminEpisodeRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return getDrama(dramaIdText);
        }

        long dramaId = numericDramaId(dramaIdText);
        int imported = 0;
        for (AdminEpisodeRequest request : requests) {
            if (request == null || safe(request.title()).isBlank()) {
                continue;
            }
            int nextIndex = request.index() == null ? nextEpisodeNumber(dramaId) : request.index();
            createEpisode(dramaIdText, new AdminEpisodeRequest(
                    request.id(),
                    nextIndex,
                    request.title(),
                    request.duration(),
                    request.status() == null ? "待审核" : request.status(),
                    request.payNode(),
                    request.cover(),
                    request.videoUrl(),
                    request.storylineId(),
                    request.unlockPrice(),
                    request.price(),
                    request.reviewNote() == null ? "批量导入，等待内容审核" : request.reviewNote()
            ));
            imported++;
        }

        auditService.record("EPISODE_BATCH_IMPORT", "drama", String.valueOf(dramaId), Map.of(
                "imported", String.valueOf(imported)
        ));
        return getDrama(dramaIdText);
    }

    @Transactional
    public AdminDramaResponse archiveEpisode(String dramaIdText, String episodeId) {
        long dramaId = numericDramaId(dramaIdText);
        AdminDramaResponse.EpisodeItem current = episode(dramaId, episodeId);
        int updated = jdbc.update(
                "UPDATE episodes SET status = 'ARCHIVED', review_note = '已下架归档' WHERE id = ? AND drama_id = ?",
                episodeId,
                dramaId
        );
        ensureUpdated(updated, "Episode not found: " + episodeId);
        auditService.record("EPISODE_ARCHIVE", "episode", episodeId, Map.of(
                "dramaId", String.valueOf(dramaId)
        ));
        recordContentReview("EPISODE", episodeId, dramaId, current.title(), "ARCHIVED", "已下架归档");
        return getDrama(dramaIdText);
    }

    private AdminDramaResponse toDramaResponse(
            long id,
            String title,
            String category,
            int episodeCount,
            String cover,
            String status,
            String owner,
            String reviewNote,
            long playCount,
            LocalDateTime updatedAt,
            int uploaded,
            int payNodes,
            int paidOrders,
            BigDecimal revenue,
            BigDecimal price
    ) {
        int total = Math.max(episodeCount, uploaded);
        int progress = total <= 0 ? 0 : Math.min(100, Math.round(uploaded * 100f / total));
        String unlockRate = playCount <= 0
                ? "待测"
                : BigDecimal.valueOf(paidOrders * 100d / playCount)
                        .setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString() + "%";
        List<String> tags = new ArrayList<>();
        tags.add(repair(category));
        if (payNodes > 0) {
            tags.add("付费节点");
        }

        return new AdminDramaResponse(
                "D" + id,
                id,
                repair(title),
                repair(category),
                toDisplayDramaStatus(status),
                total,
                uploaded,
                moneyText(price),
                unlockRate,
                revenue,
                playCount,
                repair(text(owner, "内容运营")),
                repair(reviewNote),
                updatedAt == null ? "" : updatedAt.format(TIME_TEXT),
                progress,
                cover,
                tags,
                listEpisodes(id),
                listReviewLogs(id)
        );
    }

    private List<AdminDramaResponse.EpisodeItem> listEpisodes(long dramaId) {
        return jdbc.query("""
                SELECT id, episode_no, title, duration, storyline_id, cover_url, video_url,
                       status, review_note, pay_node, unlock_price
                FROM episodes
                WHERE drama_id = ? AND status <> 'ARCHIVED'
                ORDER BY episode_no, storyline_id IS NOT NULL, storyline_id, id
                """, (rs, rowNum) -> {
            BigDecimal unlockPrice = money(rs.getBigDecimal("unlock_price"));
            return new AdminDramaResponse.EpisodeItem(
                    rs.getString("id"),
                    rs.getInt("episode_no"),
                    repair(rs.getString("title")),
                    text(rs.getString("duration"), "01:30"),
                    toDisplayEpisodeStatus(rs.getString("status")),
                    rs.getBoolean("pay_node"),
                    rs.getString("cover_url"),
                    rs.getString("video_url"),
                    rs.getString("storyline_id"),
                    unlockPrice,
                    moneyText(unlockPrice),
                    repair(rs.getString("review_note"))
            );
        }, dramaId);
    }

    private AdminDramaResponse.EpisodeItem episode(long dramaId, String episodeId) {
        return listEpisodes(dramaId).stream()
                .filter((episode) -> episode.id().equals(episodeId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found: " + episodeId));
    }

    private List<AdminContentReviewLogResponse> listReviewLogs(long dramaId) {
        return jdbc.query("""
                SELECT id, target_type, target_id, drama_id, title, status, note,
                       actor_username, created_at
                FROM admin_content_review_logs
                WHERE drama_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 40
                """, (rs, rowNum) -> new AdminContentReviewLogResponse(
                rs.getLong("id"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                longOrNull(rs.getObject("drama_id")),
                repair(rs.getString("title")),
                toDisplayContentStatus(rs.getString("target_type"), rs.getString("status")),
                repair(rs.getString("note")),
                repair(rs.getString("actor_username")),
                timeText(toLocalDateTime(rs.getTimestamp("created_at")))
        ), dramaId);
    }

    private void recordContentReview(String targetType, String targetId, Long dramaId, String title, String status, String note) {
        Actor actor = currentActor();
        jdbc.update("""
                INSERT INTO admin_content_review_logs
                (target_type, target_id, drama_id, title, status, note, actor_id, actor_username)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                targetType,
                targetId,
                dramaId,
                text(title, ""),
                text(status, "DRAFT"),
                safe(note),
                actor.id(),
                actor.username());
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppPrincipal principal) {
            return new Actor(principal.id(), principal.username());
        }
        return new Actor(null, "system");
    }

    private String toDisplayContentStatus(String targetType, String status) {
        if ("EPISODE".equalsIgnoreCase(targetType)) {
            return toDisplayEpisodeStatus(status);
        }
        if ("MEDIA_ASSET".equalsIgnoreCase(targetType)) {
            if ("APPROVED".equalsIgnoreCase(status)) return "已通过";
            if ("REJECTED".equalsIgnoreCase(status)) return "已驳回";
            return "待审核";
        }
        return toDisplayDramaStatus(status);
    }

    private String timeText(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_TEXT);
    }

    private long nextDramaId() {
        Long value = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM dramas", Long.class);
        return value == null ? 1L : value;
    }

    private int nextDramaSortOrder() {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM dramas", Integer.class);
        return value == null ? 1 : value;
    }

    private int nextEpisodeNumber(long dramaId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(episode_no), 0) + 1
                FROM episodes
                WHERE drama_id = ? AND status <> 'ARCHIVED'
                """, Integer.class, dramaId);
        return value == null ? 1 : value;
    }

    private String uniqueEpisodeId(String preferred) {
        String base = preferred.length() > 56 ? preferred.substring(0, 56) : preferred;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM episodes WHERE id = ?", Integer.class, base);
        if (count == null || count == 0) {
            return base;
        }
        return base + "-" + String.valueOf(System.currentTimeMillis()).substring(7);
    }

    private BigDecimal resolveEpisodePrice(AdminEpisodeRequest request, String fallback, boolean payNode) {
        if (!payNode) {
            return null;
        }
        if (request != null && request.unlockPrice() != null) {
            return money(request.unlockPrice());
        }
        return parseMoney(request == null ? null : request.price(), parseMoney(fallback, new BigDecimal("6.00")));
    }

    private String toInternalDramaStatus(String status, String fallback) {
        String value = safe(status);
        if (value.equals("上架") || value.equalsIgnoreCase("PUBLISHED")) return "PUBLISHED";
        if (value.equals("审核中") || value.equalsIgnoreCase("REVIEWING") || value.equalsIgnoreCase("REVIEW")) return "REVIEWING";
        if (value.equals("已下架") || value.equalsIgnoreCase("ARCHIVED") || value.equalsIgnoreCase("OFFLINE")) return "ARCHIVED";
        if (value.equals("草稿") || value.equalsIgnoreCase("DRAFT")) return "DRAFT";
        return fallback;
    }

    private String toDisplayDramaStatus(String status) {
        if ("PUBLISHED".equalsIgnoreCase(status)) return "上架";
        if ("REVIEWING".equalsIgnoreCase(status) || "REVIEW".equalsIgnoreCase(status)) return "审核中";
        if ("ARCHIVED".equalsIgnoreCase(status) || "OFFLINE".equalsIgnoreCase(status)) return "已下架";
        return "草稿";
    }

    private String defaultDramaReviewNote(String status) {
        return switch (toInternalDramaStatus(status, "DRAFT")) {
            case "PUBLISHED" -> "剧目已发布，可在前台展示";
            case "REVIEWING" -> "剧目已提交审核";
            case "ARCHIVED" -> "剧目已下架";
            default -> "剧目草稿已保存";
        };
    }

    private String toInternalEpisodeStatus(String status, String fallback) {
        String value = safe(status);
        if (value.equals("已发布") || value.equalsIgnoreCase("PUBLISHED")) return "PUBLISHED";
        if (value.equals("待审核") || value.equalsIgnoreCase("REVIEWING") || value.equalsIgnoreCase("REVIEW")) return "REVIEWING";
        if (value.equals("已下架") || value.equalsIgnoreCase("ARCHIVED") || value.equalsIgnoreCase("OFFLINE")) return "ARCHIVED";
        if (value.equals("草稿") || value.equals("待发布") || value.equalsIgnoreCase("DRAFT")) return "DRAFT";
        return fallback;
    }

    private String toDisplayEpisodeStatus(String status) {
        if ("PUBLISHED".equalsIgnoreCase(status)) return "已发布";
        if ("REVIEWING".equalsIgnoreCase(status) || "REVIEW".equalsIgnoreCase(status)) return "待审核";
        if ("ARCHIVED".equalsIgnoreCase(status) || "OFFLINE".equalsIgnoreCase(status)) return "已下架";
        return "待发布";
    }

    private String defaultEpisodeReviewNote(String status) {
        return switch (toInternalEpisodeStatus(status, "DRAFT")) {
            case "PUBLISHED" -> "剧集已发布，可在前台播放";
            case "REVIEWING" -> "剧集已提交审核";
            case "ARCHIVED" -> "剧集已下架";
            default -> "剧集待发布";
        };
    }

    private long numericDramaId(String value) {
        String digits = safe(value).replaceAll("\\D+", "");
        if (digits.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid drama id: " + value);
        }
        return Long.parseLong(digits);
    }

    private BigDecimal parseMoney(String value, BigDecimal fallback) {
        String cleaned = safe(value).replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return money(fallback);
        }
        try {
            return money(new BigDecimal(cleaned));
        } catch (NumberFormatException ex) {
            return money(fallback);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyText(BigDecimal value) {
        return "¥" + money(value).toPlainString();
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? Math.max(1, fallback) : value;
    }

    private String text(String value, String fallback) {
        String clean = safe(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String nullable(String value) {
        String clean = safe(value);
        return clean.isBlank() ? null : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void ensureUpdated(int updated, String message) {
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private record Actor(Long id, String username) {
    }
}
