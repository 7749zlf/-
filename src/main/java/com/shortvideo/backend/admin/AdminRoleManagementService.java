package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.shortvideo.backend.admin.dto.AdminPermissionResponse;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.admin.dto.AdminRoleRequest;
import com.shortvideo.backend.admin.dto.AdminRoleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminRoleManagementService {

    private static final Map<String, String> PERMISSION_LABELS = new LinkedHashMap<>();

    static {
        PERMISSION_LABELS.put("dashboard", "经营概览");
        PERMISSION_LABELS.put("content", "内容管理");
        PERMISSION_LABELS.put("storyline", "故事线池");
        PERMISSION_LABELS.put("orders", "订单管理");
        PERMISSION_LABELS.put("channels", "渠道管理");
        PERMISSION_LABELS.put("media", "素材管理");
        PERMISSION_LABELS.put("finance", "财务数据");
        PERMISSION_LABELS.put("users", "用户管理");
        PERMISSION_LABELS.put("roles", "角色权限");
        PERMISSION_LABELS.put("settings", "系统设置");
    }

    private final JdbcTemplate jdbc;
    private final AdminAuditLogService auditLogService;

    public AdminRoleManagementService(JdbcTemplate jdbc, AdminAuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    public List<AdminPermissionResponse> listPermissions() {
        return jdbc.query("""
                SELECT permission_key, permission_name, description
                FROM admin_permissions
                ORDER BY sort_order, permission_key
                """, (rs, rowNum) -> new AdminPermissionResponse(
                rs.getString("permission_key"),
                PERMISSION_LABELS.getOrDefault(rs.getString("permission_key"), repair(rs.getString("permission_name"))),
                repair(rs.getString("description"))
        ));
    }

    public List<AdminRoleResponse> listRoles() {
        return jdbc.query("""
                SELECT r.role_key, r.role_name, r.status,
                       (SELECT COUNT(*) FROM admin_users u WHERE u.role_key = r.role_key) AS members
                FROM admin_roles r
                ORDER BY r.sort_order, r.role_key
                """, (rs, rowNum) -> new AdminRoleResponse(
                rs.getString("role_key"),
                repair(rs.getString("role_name")),
                rs.getInt("members"),
                toDisplayStatus(rs.getString("status")),
                rolePermissions(rs.getString("role_key"))
        ));
    }

    public AdminRoleResponse getRole(String id) {
        return listRoles().stream()
                .filter((role) -> role.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + id));
    }

    @Transactional
    public AdminRoleResponse createRole(AdminRoleRequest request, AdminProfileResponse actor) {
        String roleKey = uniqueRoleKey();
        String name = text(request == null ? null : request.name(), "新角色");
        String status = toInternalStatus(request == null ? null : request.status(), "ENABLED");
        int sortOrder = nextSortOrder();

        jdbc.update("""
                INSERT INTO admin_roles (role_key, role_name, description, status, sort_order)
                VALUES (?, ?, '', ?, ?)
                """, roleKey, name, status, sortOrder);
        replacePermissions(roleKey, normalizedPermissions(request == null ? null : request.permissions()));
        auditLogService.record(actor, "创建角色", "角色权限", roleKey, name);
        return getRole(roleKey);
    }

    @Transactional
    public AdminRoleResponse updateRole(String id, AdminRoleRequest request, AdminProfileResponse actor) {
        AdminRoleResponse current = getRole(id);
        String name = text(request == null ? null : request.name(), current.name());
        String status = "administrator".equals(id)
                ? "ENABLED"
                : toInternalStatus(request == null ? null : request.status(), toInternalStatus(current.status(), "ENABLED"));
        List<String> permissions = "administrator".equals(id)
                ? allPermissionKeys()
                : normalizedPermissions(request == null ? null : request.permissions());

        int updated = jdbc.update("""
                UPDATE admin_roles
                SET role_name = ?, status = ?
                WHERE role_key = ?
                """, name, status, id);
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + id);
        }
        replacePermissions(id, permissions);
        auditLogService.record(actor, "保存角色", "角色权限", id, name);
        return getRole(id);
    }

    @Transactional
    public AdminRoleResponse updateStatus(String id, String status, AdminProfileResponse actor) {
        if ("administrator".equals(id)) {
            auditLogService.record(actor, "保护角色", "角色权限", id, "管理员角色保持启用");
            return getRole(id);
        }
        AdminRoleResponse current = getRole(id);
        String nextStatus = toInternalStatus(status, toInternalStatus(current.status(), "ENABLED"));
        jdbc.update("UPDATE admin_roles SET status = ? WHERE role_key = ?", nextStatus, id);
        AdminRoleResponse updated = getRole(id);
        auditLogService.record(actor, "切换角色状态", "角色权限", id, updated.status());
        return updated;
    }

    private List<String> rolePermissions(String roleKey) {
        return jdbc.query("""
                SELECT permission_key
                FROM admin_role_permissions
                WHERE role_key = ?
                ORDER BY permission_key
                """, (rs, rowNum) -> rs.getString("permission_key"), roleKey);
    }

    private void replacePermissions(String roleKey, List<String> permissions) {
        jdbc.update("DELETE FROM admin_role_permissions WHERE role_key = ?", roleKey);
        for (String permission : permissions) {
            jdbc.update("""
                    INSERT IGNORE INTO admin_role_permissions (role_key, permission_key)
                    VALUES (?, ?)
                    """, roleKey, permission);
        }
    }

    private List<String> normalizedPermissions(List<String> permissions) {
        List<String> valid = allPermissionKeys();
        List<String> selected = permissions == null ? List.of("dashboard") : permissions;
        List<String> normalized = selected.stream()
                .map(this::safe)
                .filter(valid::contains)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("dashboard") : normalized;
    }

    private List<String> allPermissionKeys() {
        return jdbc.query("""
                SELECT permission_key
                FROM admin_permissions
                ORDER BY sort_order, permission_key
                """, (rs, rowNum) -> rs.getString("permission_key"));
    }

    private String uniqueRoleKey() {
        String key;
        do {
            key = "custom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (exists(key));
        return key;
    }

    private boolean exists(String roleKey) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_roles WHERE role_key = ?", Integer.class, roleKey);
        return count != null && count > 0;
    }

    private int nextSortOrder() {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) + 10 FROM admin_roles", Integer.class);
        return value == null ? 10 : value;
    }

    private String toInternalStatus(String status, String fallback) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        if (value.equals("已启用") || value.equals("ENABLED")) return "ENABLED";
        if (value.equals("已停用") || value.equals("DISABLED")) return "DISABLED";
        return fallback;
    }

    private String toDisplayStatus(String status) {
        return "DISABLED".equalsIgnoreCase(status) ? "已停用" : "已启用";
    }

    private String text(String value, String fallback) {
        String clean = safe(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
