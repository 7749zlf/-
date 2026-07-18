package com.shortvideo.backend.admin;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSettingsService {

    private static final String SETTINGS_ID = "default";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AdminAuditLogService auditLogService;

    public AdminSettingsService(JdbcTemplate jdbc, ObjectMapper objectMapper, AdminAuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> getSettings() {
        ensureSettings();
        String json = jdbc.queryForObject(
                "SELECT payload FROM admin_settings WHERE id = ?",
                String.class,
                SETTINGS_ID
        );
        return mergeDefaults(readJson(json));
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> request, AdminProfileResponse actor) {
        ensureSettings();
        Map<String, Object> settings = mergeDefaults(request);
        jdbc.update("""
                UPDATE admin_settings
                SET payload = CAST(? AS JSON)
                WHERE id = ?
                """, writeJson(settings), SETTINGS_ID);
        auditLogService.record(actor, "保存系统设置", "系统设置", SETTINGS_ID, "后台配置已更新");
        return getSettings();
    }

    private void ensureSettings() {
        jdbc.update("""
                INSERT IGNORE INTO admin_settings (id, payload)
                VALUES (?, CAST(? AS JSON))
                """, SETTINGS_ID, writeJson(defaults()));
    }

    private Map<String, Object> mergeDefaults(Map<String, Object> source) {
        Map<String, Object> merged = defaults();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (merged.containsKey(entry.getKey())) {
                    merged.put(entry.getKey(), sanitize(entry.getKey(), entry.getValue(), merged.get(entry.getKey())));
                }
            }
        }
        return merged;
    }

    private Object sanitize(String key, Object value, Object fallback) {
        if (value == null) {
            return fallback;
        }
        if (fallback instanceof Boolean) {
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
        }
        if (fallback instanceof Number) {
            try {
                return Math.max(0, Double.valueOf(String.valueOf(value)).intValue());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("autoReview", true);
        defaults.put("watermark", true);
        defaults.put("smsNotify", true);
        defaults.put("riskControl", true);
        defaults.put("payment", "PayPal 演示通道");
        defaults.put("storage", "本地视频库");
        defaults.put("callback", "https://example.com/payment/callback");
        defaults.put("minBalance", 30);
        defaults.put("refundWindow", 24);
        defaults.put("maxDrawPerDay", 20);
        defaults.put("defaultUnlockPrice", "¥18");
        defaults.put("defaultDrawPrice", "¥6");
        return defaults;
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to write admin settings", ex);
        }
    }
}
