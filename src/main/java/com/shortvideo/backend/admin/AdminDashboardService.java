package com.shortvideo.backend.admin;

import static com.shortvideo.backend.common.TextEncodingRepair.repair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import com.shortvideo.backend.admin.dto.AdminDashboardResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final JdbcTemplate jdbc;

    public AdminDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AdminDashboardResponse summary() {
        BigDecimal todayRevenue = moneyQuery("""
                SELECT COALESCE(SUM(amount), 0)
                FROM orders
                WHERE (UPPER(status) = 'PAID' OR status = '已支付')
                  AND DATE(COALESCE(paid_at, created_at)) = CURDATE()
                """);
        BigDecimal yesterdayRevenue = moneyQuery("""
                SELECT COALESCE(SUM(amount), 0)
                FROM orders
                WHERE (UPPER(status) = 'PAID' OR status = '已支付')
                  AND DATE(COALESCE(paid_at, created_at)) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
                """);
        long totalPlays = longQuery("SELECT COALESCE(SUM(play_count), 0) FROM dramas WHERE status <> 'ARCHIVED'");
        long todayPlayEvents = longQuery("""
                SELECT COUNT(*)
                FROM play_events
                WHERE DATE(created_at) = CURDATE()
                """);
        long todayPaidOrders = longQuery("""
                SELECT COUNT(*)
                FROM orders
                WHERE (UPPER(status) = 'PAID' OR status = '已支付')
                  AND DATE(COALESCE(paid_at, created_at)) = CURDATE()
                """);
        long yesterdayPaidOrders = longQuery("""
                SELECT COUNT(*)
                FROM orders
                WHERE (UPPER(status) = 'PAID' OR status = '已支付')
                  AND DATE(COALESCE(paid_at, created_at)) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
                """);
        BigDecimal totalRevenue = moneyQuery("""
                SELECT COALESCE(SUM(amount), 0)
                FROM orders
                WHERE UPPER(status) = 'PAID' OR status = '已支付'
                """);
        BigDecimal channelCost = moneyQuery("SELECT COALESCE(SUM(spent), 0) FROM admin_channels");
        String roi = roi(totalRevenue, channelCost);

        return new AdminDashboardResponse(
                kpis(todayRevenue, yesterdayRevenue, totalPlays, todayPlayEvents, todayPaidOrders, yesterdayPaidOrders, roi),
                trend(),
                topDramas(),
                tasks()
        );
    }

    private List<AdminDashboardResponse.Kpi> kpis(
            BigDecimal todayRevenue,
            BigDecimal yesterdayRevenue,
            long totalPlays,
            long todayPlayEvents,
            long todayPaidOrders,
            long yesterdayPaidOrders,
            String roi
    ) {
        return List.of(
                new AdminDashboardResponse.Kpi(
                        "今日收入",
                        moneyText(todayRevenue),
                        percentChange(todayRevenue, yesterdayRevenue),
                        "较昨日实时计算",
                        "accent"
                ),
                new AdminDashboardResponse.Kpi(
                        "播放量",
                        compact(totalPlays),
                        todayPlayEvents > 0 ? "今日 +" + compact(todayPlayEvents) : "今日暂无新增",
                        "短剧累计播放",
                        "teal"
                ),
                new AdminDashboardResponse.Kpi(
                        "付费订单",
                        compact(todayPaidOrders),
                        percentChange(BigDecimal.valueOf(todayPaidOrders), BigDecimal.valueOf(yesterdayPaidOrders)),
                        "今日成交订单",
                        "gold"
                ),
                new AdminDashboardResponse.Kpi(
                        "投放 ROI",
                        roi,
                        "按渠道成本",
                        "收入扣除投流成本",
                        "blue"
                )
        );
    }

    private List<AdminDashboardResponse.TrendPoint> trend() {
        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        jdbc.query("""
                SELECT DATE(COALESCE(paid_at, created_at)) AS pay_day,
                       COALESCE(SUM(amount), 0) AS revenue
                FROM orders
                WHERE (UPPER(status) = 'PAID' OR status = '已支付')
                  AND DATE(COALESCE(paid_at, created_at)) >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
                GROUP BY DATE(COALESCE(paid_at, created_at))
                """, (rs) -> {
            Date day = rs.getDate("pay_day");
            if (day != null) {
                revenueByDay.put(day.toLocalDate(), money(rs.getBigDecimal("revenue")));
            }
        });

        LocalDate today = LocalDate.now();
        BigDecimal max = BigDecimal.ZERO;
        List<BigDecimal> values = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            BigDecimal value = revenueByDay.getOrDefault(today.minusDays(offset), BigDecimal.ZERO);
            values.add(value);
            if (value.compareTo(max) > 0) {
                max = value;
            }
        }

        List<AdminDashboardResponse.TrendPoint> points = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            LocalDate day = today.minusDays(6L - index);
            int heat = max.compareTo(BigDecimal.ZERO) <= 0
                    ? 0
                    : values.get(index).multiply(BigDecimal.valueOf(100))
                            .divide(max, 0, RoundingMode.HALF_UP)
                            .intValue();
            points.add(new AdminDashboardResponse.TrendPoint(
                    day.equals(today) ? "今天" : day.format(DAY_LABEL),
                    heat
            ));
        }
        return points;
    }

    private List<AdminDashboardResponse.TopDrama> topDramas() {
        return jdbc.query("""
                SELECT d.id, d.title, d.tag, d.cover_url, d.play_count,
                       COALESCE(SUM(CASE WHEN UPPER(o.status) = 'PAID' OR o.status = '已支付' THEN o.amount ELSE 0 END), 0) AS revenue,
                       COALESCE(SUM(CASE WHEN UPPER(o.status) = 'PAID' OR o.status = '已支付' THEN 1 ELSE 0 END), 0) AS paid_orders
                FROM dramas d
                LEFT JOIN orders o ON o.drama_id = d.id
                WHERE d.status <> 'ARCHIVED'
                GROUP BY d.id, d.title, d.tag, d.cover_url, d.play_count
                ORDER BY revenue DESC, d.play_count DESC, d.sort_order, d.id
                LIMIT 4
                """, (rs, rowNum) -> {
            long rawId = rs.getLong("id");
            long playCount = rs.getLong("play_count");
            long paidOrders = rs.getLong("paid_orders");
            return new AdminDashboardResponse.TopDrama(
                    "D" + rawId,
                    rawId,
                    repair(rs.getString("title")),
                    repair(rs.getString("tag")),
                    unlockRate(paidOrders, playCount),
                    money(rs.getBigDecimal("revenue")),
                    rs.getString("cover_url")
            );
        });
    }

    private List<AdminDashboardResponse.TaskItem> tasks() {
        long pendingReviews = longQuery("""
                SELECT
                  (SELECT COUNT(*) FROM dramas WHERE status = 'REVIEWING')
                + (SELECT COUNT(*) FROM episodes WHERE status = 'REVIEWING')
                + (SELECT COUNT(*) FROM admin_media_assets WHERE status = 'REVIEWING')
                """);
        long pendingRefunds = longQuery("SELECT COUNT(*) FROM h5_refund_requests WHERE status = 'PENDING_REVIEW'");
        long pendingOrders = longQuery("""
                SELECT COUNT(*)
                FROM orders
                WHERE UPPER(status) IN ('PENDING', 'PROCESSING', 'RISK', 'REVIEWING')
                   OR status IN ('处理中', '风控中')
                """);
        long lowRoiChannels = longQuery("""
                SELECT COUNT(*)
                FROM admin_channels
                WHERE status <> 'PAUSED'
                  AND spent > 0
                  AND revenue < spent
                """);
        long inactiveStoryPools = longQuery("SELECT COUNT(*) FROM story_pools WHERE status <> 'ENABLED'");

        return List.of(
                task(pendingReviews, "内容/素材待审核", "内容审核队列已清空", "content"),
                task(pendingRefunds, "笔退款待审批", "退款审批已处理完", "orders"),
                task(pendingOrders, "笔订单需要处理", "订单异常队列已清空", "orders"),
                task(lowRoiChannels, "个渠道 ROI 低于 1", "渠道 ROI 暂无低效项", "channels"),
                task(inactiveStoryPools, "个故事线池未开启", "故事线池均已开启", "storyline")
        );
    }

    private AdminDashboardResponse.TaskItem task(long count, String todoSuffix, String doneText, String panel) {
        boolean done = count <= 0;
        String text = done ? doneText : "有 " + count + " " + todoSuffix;
        return new AdminDashboardResponse.TaskItem(text, done, panel);
    }

    private String unlockRate(long paidOrders, long playCount) {
        if (playCount <= 0) {
            return paidOrders > 0 ? "待测" : "0%";
        }
        return BigDecimal.valueOf(paidOrders * 100d / playCount)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private String roi(BigDecimal revenue, BigDecimal cost) {
        if (cost.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.00";
        }
        return revenue.subtract(cost)
                .divide(cost, 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) <= 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? "+100%" : "0%";
        }
        BigDecimal percent = current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
        String sign = percent.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
        return sign + percent.stripTrailingZeros().toPlainString() + "%";
    }

    private BigDecimal moneyQuery(String sql) {
        return money(jdbc.queryForObject(sql, BigDecimal.class));
    }

    private long longQuery(String sql) {
        Number value = jdbc.queryForObject(sql, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyText(BigDecimal value) {
        return "¥" + money(value).toPlainString();
    }

    private String compact(long value) {
        if (value >= 10000) {
            return BigDecimal.valueOf(value)
                    .divide(BigDecimal.valueOf(10000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString() + "万";
        }
        return String.format(Locale.ROOT, "%d", value);
    }
}
