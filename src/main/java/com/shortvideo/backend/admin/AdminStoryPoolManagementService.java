package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shortvideo.backend.admin.dto.AdminStoryPoolRequest;
import com.shortvideo.backend.admin.dto.AdminStoryPoolResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminStoryPoolManagementService {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final String DEFAULT_COVER =
            "https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82";
    private static final List<String> RARITIES = List.of("SSR", "SR", "R");

    private final JdbcTemplate jdbc;
    private final AdminAuditService auditService;

    public AdminStoryPoolManagementService(JdbcTemplate jdbc, AdminAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<AdminStoryPoolResponse> listStoryPools(String keyword) {
        String search = safe(keyword).toLowerCase(Locale.ROOT);
        String like = "%" + search + "%";
        return jdbc.query("""
                SELECT p.id, p.drama_id, d.title AS drama_title, d.play_count,
                       p.name, p.status, p.draw_price,
                       (SELECT COUNT(*) FROM storylines s
                        WHERE s.pool_id = p.id AND s.status <> 'ARCHIVED') AS entries,
                       (SELECT COUNT(*) FROM user_unlocks u
                        JOIN storylines s ON s.id = u.storyline_id
                        WHERE s.pool_id = p.id) AS unlocked
                FROM story_pools p
                JOIN dramas d ON d.id = p.drama_id
                WHERE (
                    ? = ''
                    OR LOWER(p.name) LIKE ?
                    OR LOWER(d.title) LIKE ?
                  )
                  AND (
                    p.status = 'ENABLED'
                    OR (
                      p.id REGEXP '^S[0-9]'
                      AND EXISTS (
                        SELECT 1 FROM storylines s
                        WHERE s.pool_id = p.id AND s.status <> 'ARCHIVED'
                      )
                    )
                  )
                ORDER BY d.sort_order, p.status = 'DISABLED', p.id
                """, (rs, rowNum) -> {
            long playCount = rs.getLong("play_count");
            long unlocked = rs.getLong("unlocked");
            return new AdminStoryPoolResponse(
                    rs.getString("id"),
                    repair(rs.getString("name")),
                    repair(rs.getString("drama_title")),
                    rs.getLong("drama_id"),
                    toDisplayStatus(rs.getString("status")),
                    rs.getInt("entries"),
                    moneyText(rs.getBigDecimal("draw_price")),
                    unlocked,
                    playCount <= 0 ? "待测" : percent(unlocked * 100d / playCount),
                    listWeights(rs.getString("id"))
            );
        }, search, like, like);
    }

    public AdminStoryPoolResponse getStoryPool(String id) {
        return listStoryPools("").stream()
                .filter((pool) -> pool.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story pool not found: " + id));
    }

    @Transactional
    public AdminStoryPoolResponse createStoryPool(AdminStoryPoolRequest request) {
        String name = text(request == null ? null : request.name(), "新故事线池");
        long dramaId = resolveDramaId(request == null ? null : request.dramaId(), request == null ? null : request.drama());
        String id = uniquePoolId("S" + String.valueOf(System.currentTimeMillis()).substring(8));
        String status = toInternalStatus(request == null ? null : request.status());
        int entries = positive(request == null ? null : request.entries(), 6);
        BigDecimal price = parseMoney(request == null ? null : request.price(), new BigDecimal("6.00"));

        jdbc.update("""
                INSERT INTO story_pools (id, drama_id, name, status, draw_price)
                VALUES (?, ?, ?, ?, ?)
                """, id, dramaId, name, status, price);
        reconcileStorylines(id, dramaId, name, entries, weightsFromRequest(request), status);
        auditService.record("STORY_POOL_CREATE", "story_pool", id, Map.of(
                "dramaId", String.valueOf(dramaId),
                "status", status,
                "entries", String.valueOf(entries),
                "price", price.toPlainString()
        ));
        return getStoryPool(id);
    }

    @Transactional
    public AdminStoryPoolResponse updateStoryPool(String id, AdminStoryPoolRequest request) {
        AdminStoryPoolResponse current = getStoryPool(id);
        String name = text(request == null ? null : request.name(), current.name());
        long dramaId = resolveDramaId(
                request == null ? null : request.dramaId(),
                request == null ? null : request.drama(),
                current.rawDramaId()
        );
        String status = toInternalStatus(request == null ? null : request.status());
        int entries = positive(request == null ? null : request.entries(), current.entries());
        BigDecimal price = parseMoney(request == null ? null : request.price(), parseMoney(current.price(), new BigDecimal("6.00")));

        int updated = jdbc.update("""
                UPDATE story_pools
                SET drama_id = ?, name = ?, status = ?, draw_price = ?
                WHERE id = ?
                """, dramaId, name, status, price, id);
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Story pool not found: " + id);
        }
        reconcileStorylines(id, dramaId, name, entries, weightsFromRequest(request, current.weights()), status);
        auditService.record("STORY_POOL_UPDATE", "story_pool", id, Map.of(
                "dramaId", String.valueOf(dramaId),
                "status", status,
                "entries", String.valueOf(entries),
                "price", price.toPlainString()
        ));
        return getStoryPool(id);
    }

    @Transactional
    public AdminStoryPoolResponse updateStatus(String id, String status) {
        AdminStoryPoolResponse current = getStoryPool(id);
        AdminStoryPoolRequest request = new AdminStoryPoolRequest(
                current.name(),
                current.drama(),
                current.rawDramaId(),
                status,
                Math.max(current.entries(), 1),
                current.price(),
                current.weights().stream()
                        .map((weight) -> new AdminStoryPoolRequest.WeightItem(weight.label(), weight.value()))
                        .toList()
        );
        return updateStoryPool(id, request);
    }

    private List<AdminStoryPoolResponse.WeightItem> listWeights(String poolId) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (String rarity : RARITIES) {
            sums.put(rarity, BigDecimal.ZERO);
        }
        jdbc.query("""
                SELECT rarity, COALESCE(SUM(weight), 0) AS weight_sum
                FROM storylines
                WHERE pool_id = ? AND status <> 'ARCHIVED'
                GROUP BY rarity
                """, (rs) -> {
            sums.put(rs.getString("rarity").toUpperCase(Locale.ROOT), money(rs.getBigDecimal("weight_sum")));
        }, poolId);
        BigDecimal total = sums.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return defaultWeights();
        }
        return RARITIES.stream()
                .map((rarity) -> new AdminStoryPoolResponse.WeightItem(
                        rarity,
                        sums.getOrDefault(rarity, BigDecimal.ZERO)
                                .multiply(new BigDecimal("100"))
                                .divide(total, 1, RoundingMode.HALF_UP)
                                .doubleValue()
                ))
                .toList();
    }

    private void reconcileStorylines(
            String poolId,
            long dramaId,
            String poolName,
            int entries,
            List<AdminStoryPoolRequest.WeightItem> weights,
            String poolStatus
    ) {
        List<String> desiredRarities = desiredRarities(entries, weights);
        List<StorylineRow> rows = activeStorylines(poolId);

        while (rows.size() < desiredRarities.size()) {
            String id = uniqueStorylineId(poolId + "-" + (rows.size() + 1));
            jdbc.update("""
                    INSERT INTO storylines
                    (id, drama_id, pool_id, name, rarity, description, cover_url, weight, status, sort_order)
                    VALUES (?, ?, ?, ?, 'R', ?, ?, 1, ?, ?)
                    """,
                    id,
                    dramaId,
                    poolId,
                    poolName + "故事线 " + (rows.size() + 1),
                    poolName + "自动生成故事线",
                    DEFAULT_COVER,
                    "ENABLED".equals(poolStatus) ? "ENABLED" : "DISABLED",
                    rows.size() + 1
            );
            rows = activeStorylines(poolId);
        }

        if (rows.size() > desiredRarities.size()) {
            rows.stream()
                    .skip(desiredRarities.size())
                    .forEach((row) -> jdbc.update("UPDATE storylines SET status = 'ARCHIVED' WHERE id = ?", row.id()));
            rows = rows.stream().limit(desiredRarities.size()).toList();
        }

        Map<String, Long> counts = desiredRarities.stream()
                .collect(LinkedHashMap::new, (map, rarity) -> map.put(rarity, map.getOrDefault(rarity, 0L) + 1L), Map::putAll);
        Map<String, BigDecimal> weightMap = requestWeightMap(weights);

        for (int index = 0; index < rows.size(); index++) {
            StorylineRow row = rows.get(index);
            String rarity = desiredRarities.get(index);
            BigDecimal totalWeight = weightMap.getOrDefault(rarity, BigDecimal.ZERO);
            long count = Math.max(1L, counts.getOrDefault(rarity, 1L));
            BigDecimal rowWeight = totalWeight.divide(new BigDecimal(count), 3, RoundingMode.HALF_UP);
            jdbc.update("""
                    UPDATE storylines
                    SET drama_id = ?, name = ?, rarity = ?, description = ?, weight = ?,
                        status = ?, sort_order = ?
                    WHERE id = ?
                    """,
                    dramaId,
                    poolName + " " + rarity + "线 " + (index + 1),
                    rarity,
                    poolName + "自动生成故事线",
                    rowWeight,
                    "ENABLED".equals(poolStatus) ? "ENABLED" : "DISABLED",
                    index + 1,
                    row.id()
            );
        }
    }

    private List<StorylineRow> activeStorylines(String poolId) {
        return jdbc.query("""
                SELECT id, rarity, sort_order
                FROM storylines
                WHERE pool_id = ? AND status <> 'ARCHIVED'
                ORDER BY sort_order, id
                """, (rs, rowNum) -> new StorylineRow(
                rs.getString("id"),
                rs.getString("rarity"),
                rs.getInt("sort_order")
        ), poolId);
    }

    private List<String> desiredRarities(int entries, List<AdminStoryPoolRequest.WeightItem> weights) {
        Map<String, BigDecimal> weightMap = requestWeightMap(weights);
        BigDecimal total = weightMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            weightMap = requestWeightMap(defaultRequestWeights());
            total = weightMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        int assigned = 0;
        for (String rarity : RARITIES) {
            int count = weightMap.getOrDefault(rarity, BigDecimal.ZERO)
                    .multiply(new BigDecimal(entries))
                    .divide(total, 0, RoundingMode.DOWN)
                    .intValue();
            counts.put(rarity, count);
            assigned += count;
        }
        while (assigned < entries) {
            String rarity = "R";
            BigDecimal bestWeight = BigDecimal.ZERO;
            for (String item : RARITIES) {
                BigDecimal candidate = weightMap.getOrDefault(item, BigDecimal.ZERO);
                if (candidate.compareTo(bestWeight) >= 0) {
                    rarity = item;
                    bestWeight = candidate;
                }
            }
            counts.put(rarity, counts.getOrDefault(rarity, 0) + 1);
            assigned++;
        }

        List<String> result = new ArrayList<>();
        for (String rarity : RARITIES) {
            for (int index = 0; index < counts.getOrDefault(rarity, 0); index++) {
                result.add(rarity);
            }
        }
        return result.isEmpty() ? List.of("R") : result;
    }

    private Map<String, BigDecimal> requestWeightMap(List<AdminStoryPoolRequest.WeightItem> weights) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String rarity : RARITIES) {
            map.put(rarity, BigDecimal.ZERO);
        }
        for (AdminStoryPoolRequest.WeightItem weight : weights == null ? List.<AdminStoryPoolRequest.WeightItem>of() : weights) {
            String label = safe(weight.label()).toUpperCase(Locale.ROOT);
            if (RARITIES.contains(label)) {
                map.put(label, BigDecimal.valueOf(weight.value() == null ? 0d : Math.max(0d, weight.value())));
            }
        }
        return map;
    }

    private List<AdminStoryPoolRequest.WeightItem> weightsFromRequest(AdminStoryPoolRequest request) {
        return weightsFromRequest(request, defaultWeights());
    }

    private List<AdminStoryPoolRequest.WeightItem> weightsFromRequest(
            AdminStoryPoolRequest request,
            List<AdminStoryPoolResponse.WeightItem> fallback
    ) {
        if (request != null && request.weights() != null && !request.weights().isEmpty()) {
            return request.weights();
        }
        return fallback.stream()
                .map((weight) -> new AdminStoryPoolRequest.WeightItem(weight.label(), weight.value()))
                .toList();
    }

    private List<AdminStoryPoolRequest.WeightItem> defaultRequestWeights() {
        return defaultWeights().stream()
                .map((weight) -> new AdminStoryPoolRequest.WeightItem(weight.label(), weight.value()))
                .toList();
    }

    private List<AdminStoryPoolResponse.WeightItem> defaultWeights() {
        return List.of(
                new AdminStoryPoolResponse.WeightItem("SSR", 2d),
                new AdminStoryPoolResponse.WeightItem("SR", 23d),
                new AdminStoryPoolResponse.WeightItem("R", 75d)
        );
    }

    private long resolveDramaId(Long requestedDramaId, String dramaText) {
        return resolveDramaId(requestedDramaId, dramaText, 0L);
    }

    private long resolveDramaId(Long requestedDramaId, String dramaText, long fallback) {
        if (requestedDramaId != null && requestedDramaId > 0) {
            return requestedDramaId;
        }
        String drama = safe(dramaText);
        if (!drama.isBlank()) {
            Matcher matcher = DIGITS.matcher(drama);
            if (matcher.find()) {
                try {
                    long id = Long.parseLong(matcher.group());
                    if (exists("SELECT COUNT(*) FROM dramas WHERE id = ?", id)) {
                        return id;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to title lookup.
                }
            }
            Long byTitle = jdbc.query("""
                    SELECT id FROM dramas
                    WHERE title = ?
                    ORDER BY id
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getLong("id"), drama).stream().findFirst().orElse(null);
            if (byTitle != null) {
                return byTitle;
            }
        }
        if (fallback > 0) {
            return fallback;
        }
        return jdbc.query("SELECT id FROM dramas ORDER BY sort_order, id LIMIT 1", (rs, rowNum) -> rs.getLong("id"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No drama is available"));
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private String uniquePoolId(String preferred) {
        String id = preferred.length() > 56 ? preferred.substring(0, 56) : preferred;
        while (exists("SELECT COUNT(*) FROM story_pools WHERE id = ?", id)) {
            id = "S" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        return id;
    }

    private String uniqueStorylineId(String preferred) {
        String id = preferred.length() > 56 ? preferred.substring(0, 56) : preferred;
        while (exists("SELECT COUNT(*) FROM storylines WHERE id = ?", id)) {
            id = preferred + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            if (id.length() > 64) {
                id = id.substring(0, 64);
            }
        }
        return id;
    }

    private String toInternalStatus(String status) {
        String value = safe(status);
        if (value.equals("开启") || value.equalsIgnoreCase("ENABLED")) {
            return "ENABLED";
        }
        return "DISABLED";
    }

    private String toDisplayStatus(String status) {
        return "ENABLED".equalsIgnoreCase(status) ? "开启" : "暂停";
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

    private String percent(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? Math.max(1, fallback) : value;
    }

    private String text(String value, String fallback) {
        String clean = safe(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record StorylineRow(String id, String rarity, int sortOrder) {
    }
}
