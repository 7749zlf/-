package com.shortvideo.backend.admin;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideo.backend.security.AppPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminAuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String targetType, String targetId, Map<String, ?> details) {
        try {
            Actor actor = currentActor();
            jdbc.update("""
                    INSERT INTO admin_audit_logs
                    (actor_id, actor_username, action, target_type, target_id, details)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS JSON))
                    """,
                    actor.id(),
                    actor.username(),
                    clean(action, "UNKNOWN"),
                    clean(targetType, "UNKNOWN"),
                    clean(targetId, ""),
                    writeJson(details));
        } catch (Exception ex) {
            log.warn("Admin audit log write failed: action={}, targetType={}, targetId={}",
                    action, targetType, targetId, ex);
        }
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppPrincipal principal) {
            return new Actor(principal.id(), principal.username());
        }
        return new Actor(null, "system");
    }

    private String writeJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String clean(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isBlank() ? fallback : text;
    }

    private record Actor(Long id, String username) {
    }
}
