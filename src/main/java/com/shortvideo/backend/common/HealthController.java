package com.shortvideo.backend.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final String avatarUploadDir;

    public HealthController(
            JdbcTemplate jdbc,
            @Value("${app.storage.avatar-upload-dir:uploads/avatars}") String avatarUploadDir
    ) {
        this.jdbc = jdbc;
        this.avatarUploadDir = avatarUploadDir;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/health/ready")
    public ResponseEntity<Map<String, String>> ready() {
        Map<String, String> checks = new LinkedHashMap<>();
        boolean databaseReady = databaseReady();
        boolean storageReady = storageReady();

        checks.put("status", databaseReady && storageReady ? "ok" : "degraded");
        checks.put("database", databaseReady ? "ok" : "failed");
        checks.put("storage", storageReady ? "ok" : "failed");

        return ResponseEntity
                .status(databaseReady && storageReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(checks);
    }

    private boolean databaseReady() {
        try {
            Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean storageReady() {
        try {
            Path uploadDir = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            return Files.isDirectory(uploadDir) && Files.isWritable(uploadDir);
        } catch (Exception ex) {
            return false;
        }
    }
}
