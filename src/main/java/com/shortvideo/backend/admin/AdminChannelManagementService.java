package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.shortvideo.backend.admin.dto.AdminChannelRequest;
import com.shortvideo.backend.admin.dto.AdminChannelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminChannelManagementService {

    private final JdbcTemplate jdbc;

    public AdminChannelManagementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminChannelResponse> listChannels(String keyword) {
        String search = safe(keyword).toLowerCase(Locale.ROOT);
        String like = "%" + search + "%";
        return jdbc.query("""
                SELECT id, name, source, owner, status, budget, spent, revenue, installs, pay_users
                FROM admin_channels
                WHERE ? = ''
                   OR LOWER(name) LIKE ?
                   OR LOWER(source) LIKE ?
                   OR LOWER(owner) LIKE ?
                   OR LOWER(status) LIKE ?
                ORDER BY status = 'RUNNING' DESC, updated_at DESC, id
                """, (rs, rowNum) -> toResponse(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("source"),
                rs.getString("owner"),
                rs.getString("status"),
                rs.getBigDecimal("budget"),
                rs.getBigDecimal("spent"),
                rs.getBigDecimal("revenue"),
                rs.getInt("installs"),
                rs.getInt("pay_users")
        ), search, like, like, like, like);
    }

    public AdminChannelResponse getChannel(String id) {
        return listChannels("").stream()
                .filter((channel) -> channel.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found: " + id));
    }

    @Transactional
    public AdminChannelResponse createChannel(AdminChannelRequest request) {
        String id = uniqueId();
        String name = text(request == null ? null : request.name(), "新渠道计划");
        String source = text(request == null ? null : request.source(), "短视频投流");
        String owner = text(request == null ? null : request.owner(), "增长组");
        String status = toInternalStatus(request == null ? null : request.status(), "WATCHING");
        BigDecimal budget = money(request == null ? null : request.budget());
        BigDecimal spent = money(request == null ? null : request.spent());
        BigDecimal revenue = money(request == null ? null : request.revenue());
        int installs = positive(request == null ? null : request.installs());
        int payUsers = positive(request == null ? null : request.payUsers());

        jdbc.update("""
                INSERT INTO admin_channels
                (id, name, source, owner, status, budget, spent, revenue, installs, pay_users)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, name, source, owner, status, budget, spent, revenue, installs, payUsers);
        return getChannel(id);
    }

    @Transactional
    public AdminChannelResponse updateChannel(String id, AdminChannelRequest request) {
        AdminChannelResponse current = getChannel(id);
        String name = text(request == null ? null : request.name(), current.name());
        String source = text(request == null ? null : request.source(), current.source());
        String owner = text(request == null ? null : request.owner(), current.owner());
        String status = toInternalStatus(request == null ? null : request.status(), toInternalStatus(current.status(), "WATCHING"));
        BigDecimal budget = request != null && request.budget() != null ? money(request.budget()) : current.budget();
        BigDecimal spent = request != null && request.spent() != null ? money(request.spent()) : current.spent();
        BigDecimal revenue = request != null && request.revenue() != null ? money(request.revenue()) : current.revenue();
        int installs = request != null && request.installs() != null ? positive(request.installs()) : current.installs();
        int payUsers = request != null && request.payUsers() != null ? positive(request.payUsers()) : current.payUsers();

        int updated = jdbc.update("""
                UPDATE admin_channels
                SET name = ?, source = ?, owner = ?, status = ?, budget = ?,
                    spent = ?, revenue = ?, installs = ?, pay_users = ?
                WHERE id = ?
                """, name, source, owner, status, budget, spent, revenue, installs, payUsers, id);
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found: " + id);
        }
        return getChannel(id);
    }

    @Transactional
    public AdminChannelResponse updateStatus(String id, String status) {
        AdminChannelResponse current = getChannel(id);
        return updateChannel(id, new AdminChannelRequest(
                current.name(),
                current.source(),
                current.owner(),
                status,
                current.budget(),
                current.spent(),
                current.revenue(),
                current.installs(),
                current.payUsers()
        ));
    }

    private AdminChannelResponse toResponse(
            String id,
            String name,
            String source,
            String owner,
            String status,
            BigDecimal budget,
            BigDecimal spent,
            BigDecimal revenue,
            int installs,
            int payUsers
    ) {
        BigDecimal spentMoney = money(spent);
        BigDecimal revenueMoney = money(revenue);
        return new AdminChannelResponse(
                id,
                repair(name),
                repair(source),
                repair(owner),
                toDisplayStatus(status),
                money(budget),
                spentMoney,
                revenueMoney,
                installs,
                payUsers,
                roi(revenueMoney, spentMoney)
        );
    }

    private String uniqueId() {
        String id;
        do {
            id = "C" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        } while (exists(id));
        return id;
    }

    private boolean exists(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_channels WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private String toInternalStatus(String status, String fallback) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        if (value.equals("投放中") || value.equals("RUNNING")) return "RUNNING";
        if (value.equals("观察中") || value.equals("WATCHING") || value.equals("OBSERVING")) return "WATCHING";
        if (value.equals("暂停") || value.equals("PAUSED") || value.equals("DISABLED")) return "PAUSED";
        return fallback;
    }

    private String toDisplayStatus(String status) {
        return switch (toInternalStatus(status, "WATCHING")) {
            case "RUNNING" -> "投放中";
            case "PAUSED" -> "暂停";
            default -> "观察中";
        };
    }

    private String roi(BigDecimal revenue, BigDecimal spent) {
        if (spent.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.00";
        }
        return revenue.subtract(spent)
                .divide(spent, 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private BigDecimal money(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            amount = BigDecimal.ZERO;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private int positive(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private String text(String value, String fallback) {
        String clean = safe(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
