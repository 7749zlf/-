package com.shortvideo.backend.admin;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shortvideo.backend.admin.dto.H5SnapshotPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSnapshotService {

    private static final String SNAPSHOT_ID = "default";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final H5CatalogSyncService catalogSyncService;

    public AdminSnapshotService(JdbcTemplate jdbc, ObjectMapper objectMapper, H5CatalogSyncService catalogSyncService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.catalogSyncService = catalogSyncService;
    }

    public JsonNode loadSnapshot() {
        return jdbc.query("""
                SELECT JSON_PRETTY(payload) AS payload
                FROM admin_snapshots
                WHERE id = ?
                """, (rs, rowNum) -> readJson(rs.getString("payload")), SNAPSHOT_ID)
                .stream()
                .findFirst()
                .orElseGet(objectMapper::createObjectNode);
    }

    @Transactional
    public JsonNode saveSnapshot(JsonNode snapshot) {
        JsonNode safeSnapshot = snapshot == null ? objectMapper.createObjectNode() : snapshot;
        String payload = writeJson(safeSnapshot);
        jdbc.update("""
                INSERT INTO admin_snapshots (id, payload)
                VALUES (?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE payload = CAST(? AS JSON), updated_at = CURRENT_TIMESTAMP
                """, SNAPSHOT_ID, payload, payload);
        // Keep the quick snapshot workflow, but project its H5 payload into
        // queryable tables so the mobile app does not depend on admin JSON.
        toH5Payload(safeSnapshot).ifPresent(catalogSyncService::sync);
        return safeSnapshot;
    }

    private Optional<H5SnapshotPayload> toH5Payload(JsonNode snapshot) {
        JsonNode h5Node = snapshot.path("h5");
        if (h5Node.isMissingNode() || h5Node.isNull() || !h5Node.isObject()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.treeToValue(h5Node, H5SnapshotPayload.class));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid h5 snapshot payload: " + ex.getMessage(), ex);
        }
    }

    private ObjectNode readJson(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
            return objectMapper.createObjectNode();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid admin snapshot payload");
        }
    }

    private String writeJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid admin snapshot payload");
        }
    }
}
