package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shortvideo.backend.admin.dto.AdminMediaAssetRequest;
import com.shortvideo.backend.admin.dto.AdminMediaAssetResponse;
import com.shortvideo.backend.security.AppPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminMediaAssetManagementService {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String DEFAULT_COVER =
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=720&q=82";

    private final JdbcTemplate jdbc;
    private final AdminAuditService auditService;

    public AdminMediaAssetManagementService(JdbcTemplate jdbc, AdminAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<AdminMediaAssetResponse> listAssets(String keyword) {
        String search = safe(keyword).toLowerCase(Locale.ROOT);
        String like = "%" + search + "%";
        return jdbc.query("""
                SELECT m.id, m.title, m.asset_type, m.drama_id,
                       COALESCE(d.title, m.drama_title) AS drama_title,
                       m.duration, m.file_size, m.status, m.usage_scene,
                       m.owner, m.review_note, m.cover_url, m.video_url, m.updated_at
                FROM admin_media_assets m
                LEFT JOIN dramas d ON d.id = m.drama_id
                WHERE ? = ''
                   OR LOWER(m.title) LIKE ?
                   OR LOWER(m.asset_type) LIKE ?
                   OR LOWER(COALESCE(d.title, m.drama_title)) LIKE ?
                   OR LOWER(m.usage_scene) LIKE ?
                   OR LOWER(m.owner) LIKE ?
                   OR LOWER(m.status) LIKE ?
                ORDER BY m.updated_at DESC, m.id
                """, (rs, rowNum) -> toResponse(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("asset_type"),
                longOrNull(rs.getObject("drama_id")),
                rs.getString("drama_title"),
                rs.getString("duration"),
                rs.getString("file_size"),
                rs.getString("status"),
                rs.getString("usage_scene"),
                rs.getString("owner"),
                rs.getString("review_note"),
                rs.getString("cover_url"),
                rs.getString("video_url"),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        ), search, like, like, like, like, like, like);
    }

    public AdminMediaAssetResponse getAsset(String id) {
        return listAssets("").stream()
                .filter((asset) -> asset.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found: " + id));
    }

    @Transactional
    public AdminMediaAssetResponse createAsset(AdminMediaAssetRequest request) {
        String id = uniqueId();
        String title = text(request == null ? null : request.title(), "新素材");
        String type = text(request == null ? null : request.type(), "正片");
        DramaRef drama = resolveDrama(request == null ? null : request.dramaId(), request == null ? null : request.drama(), null);
        String duration = text(request == null ? null : request.duration(), defaultDuration(type));
        String size = text(request == null ? null : request.size(), defaultSize(type));
        String status = toInternalStatus(request == null ? null : request.status(), "REVIEWING");
        String usage = text(request == null ? null : request.usage(), "未绑定");
        String owner = text(request == null ? null : request.owner(), "运营");
        String reviewNote = text(request == null ? null : request.reviewNote(), defaultReviewNote(status));
        String cover = text(request == null ? null : request.cover(), DEFAULT_COVER);
        String videoUrl = safe(request == null ? null : request.videoUrl());

        jdbc.update("""
                INSERT INTO admin_media_assets
                (id, title, asset_type, drama_id, drama_title, duration, file_size,
                 status, usage_scene, owner, review_note, cover_url, video_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, title, type, drama.id(), drama.title(), duration, size,
                status, usage, owner, reviewNote, cover, videoUrl);
        auditService.record("MEDIA_ASSET_CREATE", "media_asset", id, Map.of(
                "title", title,
                "status", status
        ));
        recordContentReview(id, drama.id(), title, status, reviewNote);
        return getAsset(id);
    }

    @Transactional
    public AdminMediaAssetResponse updateAsset(String id, AdminMediaAssetRequest request) {
        AdminMediaAssetResponse current = getAsset(id);
        String title = text(request == null ? null : request.title(), current.title());
        String type = text(request == null ? null : request.type(), current.type());
        DramaRef drama = resolveDrama(
                request == null ? null : request.dramaId(),
                request == null ? null : request.drama(),
                new DramaRef(current.rawDramaId(), current.drama())
        );
        String duration = text(request == null ? null : request.duration(), current.duration());
        String size = text(request == null ? null : request.size(), current.size());
        String status = toInternalStatus(request == null ? null : request.status(), toInternalStatus(current.status(), "REVIEWING"));
        String usage = text(request == null ? null : request.usage(), current.usage());
        String owner = text(request == null ? null : request.owner(), current.owner());
        String reviewNote = request != null && request.reviewNote() != null
                ? safe(request.reviewNote())
                : text(current.reviewNote(), defaultReviewNote(status));
        String cover = text(request == null ? null : request.cover(), current.cover());
        String videoUrl = request != null && request.videoUrl() != null ? safe(request.videoUrl()) : current.videoUrl();

        int updated = jdbc.update("""
                UPDATE admin_media_assets
                SET title = ?, asset_type = ?, drama_id = ?, drama_title = ?, duration = ?,
                    file_size = ?, status = ?, usage_scene = ?, owner = ?, review_note = ?,
                    cover_url = ?, video_url = ?
                WHERE id = ?
                """, title, type, drama.id(), drama.title(), duration, size, status, usage, owner,
                reviewNote, cover, videoUrl, id);
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found: " + id);
        }
        auditService.record("MEDIA_ASSET_UPDATE", "media_asset", id, Map.of(
                "title", title,
                "status", status
        ));
        recordContentReview(id, drama.id(), title, status, reviewNote);
        return getAsset(id);
    }

    @Transactional
    public AdminMediaAssetResponse updateStatus(String id, String statusText) {
        AdminMediaAssetResponse current = getAsset(id);
        String status = toInternalStatus(statusText, "REVIEWING");
        return updateAsset(id, new AdminMediaAssetRequest(
                current.title(),
                current.type(),
                current.drama(),
                current.rawDramaId(),
                current.duration(),
                current.size(),
                statusText,
                current.usage(),
                current.owner(),
                current.updated(),
                defaultReviewNote(status),
                current.cover(),
                current.videoUrl()
        ));
    }

    private AdminMediaAssetResponse toResponse(
            String id,
            String title,
            String type,
            Long dramaId,
            String drama,
            String duration,
            String size,
            String status,
            String usage,
            String owner,
            String reviewNote,
            String cover,
            String videoUrl,
            LocalDateTime updatedAt
    ) {
        String internalStatus = toInternalStatus(status, "REVIEWING");
        return new AdminMediaAssetResponse(
                id,
                repair(title),
                repair(type),
                repair(drama),
                dramaId,
                text(duration, "-"),
                text(size, ""),
                toDisplayStatus(internalStatus),
                repair(usage),
                repair(owner),
                updatedAt == null ? "" : updatedAt.format(TIME_TEXT),
                repair(text(reviewNote, defaultReviewNote(internalStatus))),
                text(cover, DEFAULT_COVER),
                videoUrl == null ? "" : videoUrl
        );
    }

    private DramaRef resolveDrama(Long dramaId, String dramaText, DramaRef fallback) {
        if (dramaId != null && dramaId > 0) {
            DramaRef ref = dramaById(dramaId);
            if (ref != null) {
                return ref;
            }
        }

        String drama = safe(dramaText);
        if (!drama.isBlank()) {
            Matcher matcher = DIGITS.matcher(drama);
            if (matcher.find()) {
                try {
                    DramaRef ref = dramaById(Long.parseLong(matcher.group()));
                    if (ref != null) {
                        return ref;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to title lookup.
                }
            }
            DramaRef byTitle = jdbc.query("""
                    SELECT id, title
                    FROM dramas
                    WHERE title = ?
                    ORDER BY sort_order, id
                    LIMIT 1
                    """, (rs, rowNum) -> new DramaRef(rs.getLong("id"), rs.getString("title")), drama)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (byTitle != null) {
                return byTitle;
            }
            return new DramaRef(null, drama);
        }

        if (fallback != null && !safe(fallback.title()).isBlank()) {
            return fallback;
        }

        return jdbc.query("""
                SELECT id, title
                FROM dramas
                ORDER BY sort_order, id
                LIMIT 1
                """, (rs, rowNum) -> new DramaRef(rs.getLong("id"), rs.getString("title")))
                .stream()
                .findFirst()
                .orElse(new DramaRef(null, "未关联剧目"));
    }

    private DramaRef dramaById(long dramaId) {
        return jdbc.query("""
                SELECT id, title
                FROM dramas
                WHERE id = ?
                """, (rs, rowNum) -> new DramaRef(rs.getLong("id"), rs.getString("title")), dramaId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String uniqueId() {
        String id;
        do {
            id = "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        } while (exists(id));
        return id;
    }

    private boolean exists(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_media_assets WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private String toInternalStatus(String status, String fallback) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        if (value.equals("已通过") || value.equals("APPROVED")) return "APPROVED";
        if (value.equals("待审核") || value.equals("REVIEWING") || value.equals("REVIEW")) return "REVIEWING";
        if (value.equals("已驳回") || value.equals("REJECTED")) return "REJECTED";
        return fallback;
    }

    private String toDisplayStatus(String status) {
        return switch (toInternalStatus(status, "REVIEWING")) {
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            default -> "待审核";
        };
    }

    private String defaultReviewNote(String status) {
        return switch (toInternalStatus(status, "REVIEWING")) {
            case "APPROVED" -> "审核通过，可用于前台";
            case "REJECTED" -> "审核驳回，请重新处理素材";
            default -> "等待内容审核";
        };
    }

    private String defaultDuration(String type) {
        return "封面".equals(safe(type)) ? "-" : "00:30";
    }

    private String defaultSize(String type) {
        return "封面".equals(safe(type)) ? "6 MB" : "48 MB";
    }

    private void recordContentReview(String assetId, Long dramaId, String title, String status, String note) {
        Actor actor = currentActor();
        jdbc.update("""
                INSERT INTO admin_content_review_logs
                (target_type, target_id, drama_id, title, status, note, actor_id, actor_username)
                VALUES ('MEDIA_ASSET', ?, ?, ?, ?, ?, ?, ?)
                """,
                assetId,
                dramaId,
                text(title, ""),
                toInternalStatus(status, "REVIEWING"),
                text(note, defaultReviewNote(status)),
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

    private Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String text(String value, String fallback) {
        String clean = safe(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record DramaRef(Long id, String title) {
    }

    private record Actor(Long id, String username) {
    }
}
