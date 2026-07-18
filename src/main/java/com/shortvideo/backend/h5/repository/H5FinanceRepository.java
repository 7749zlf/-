package com.shortvideo.backend.h5.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class H5FinanceRepository {

    private final JdbcTemplate jdbc;

    public H5FinanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal paidAmount(long userId) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM orders
                WHERE user_id = ? AND status = 'PAID'
                """, BigDecimal.class, userId);
        return value == null ? BigDecimal.ZERO : value;
    }

    public int orderCount(long userId) {
        return count("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId);
    }

    public int rechargeCount(long userId) {
        return count("SELECT COUNT(*) FROM recharge_records WHERE user_id = ?", userId);
    }

    public int followedDramaCount(long userId) {
        return count("SELECT COUNT(*) FROM user_follows WHERE user_id = ?", userId);
    }

    public int likedEpisodeCount(long userId) {
        return count("SELECT COUNT(*) FROM episode_likes WHERE user_id = ?", userId);
    }

    public int watchHistoryCount(long userId) {
        return count("SELECT COUNT(*) FROM watch_history WHERE user_id = ?", userId);
    }

    public int unlockedStorylineCount(long userId) {
        return count("SELECT COUNT(*) FROM user_unlocks WHERE user_id = ?", userId);
    }

    public boolean hasFollowedDrama(long userId, long dramaId) {
        return exists("""
                SELECT COUNT(*)
                FROM user_follows
                WHERE user_id = ? AND drama_id = ?
                """, userId, dramaId);
    }

    public boolean hasLikedEpisode(long userId, String episodeId) {
        return exists("""
                SELECT COUNT(*)
                FROM episode_likes
                WHERE user_id = ? AND episode_id = ?
                """, userId, episodeId);
    }

    public void createPaidRecharge(String id, long userId, BigDecimal amount, String methodKey, String methodName) {
        jdbc.update("""
                INSERT INTO recharge_records (id, user_id, amount, method_key, method_name, status)
                VALUES (?, ?, ?, ?, ?, 'PAID')
                """, id, userId, amount, methodKey, methodName);
    }

    public void incrementBalance(long userId, BigDecimal amount) {
        jdbc.update("UPDATE app_users SET balance = balance + ? WHERE id = ?", amount, userId);
    }

    public List<RechargeRecord> findRechargeRecords(long userId) {
        return jdbc.query("""
                SELECT id, amount, method_key, method_name, status, created_at
                FROM recharge_records
                WHERE user_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> new RechargeRecord(
                rs.getString("id"),
                amountOrZero(rs.getBigDecimal("amount")),
                rs.getString("method_key"),
                rs.getString("method_name"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), userId);
    }

    public void createPendingPayment(
            String id,
            long userId,
            long dramaId,
            String storylineId,
            BigDecimal amount,
            String methodKey,
            String methodName
    ) {
        jdbc.update("""
                INSERT INTO h5_payments
                (id, user_id, drama_id, storyline_id, amount, method_key, method_name, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """, id, userId, dramaId, storylineId, amount, methodKey, methodName);
    }

    public Optional<PaymentRecord> findPayment(String paymentId) {
        return jdbc.query("""
                SELECT id, user_id, drama_id, storyline_id, amount, method_key, status
                FROM h5_payments
                WHERE id = ?
                """, (rs, rowNum) -> new PaymentRecord(
                rs.getString("id"),
                rs.getLong("user_id"),
                rs.getLong("drama_id"),
                rs.getString("storyline_id"),
                amountOrZero(rs.getBigDecimal("amount")),
                rs.getString("method_key"),
                rs.getString("status")
        ), paymentId).stream().findFirst();
    }

    public Optional<PaymentSummary> findPaymentSummary(String paymentId) {
        return jdbc.query("""
                SELECT id, status, amount, method_key, method_name
                FROM h5_payments
                WHERE id = ?
                """, (rs, rowNum) -> new PaymentSummary(
                rs.getString("id"),
                rs.getString("status"),
                amountOrZero(rs.getBigDecimal("amount")),
                rs.getString("method_key"),
                rs.getString("method_name")
        ), paymentId).stream().findFirst();
    }

    public int updatePaymentFromCallback(String paymentId, String status, String providerTradeNo) {
        return jdbc.update("""
                UPDATE h5_payments
                SET status = ?,
                    provider_trade_no = COALESCE(NULLIF(?, ''), provider_trade_no),
                    paid_at = CASE WHEN ? = 'PAID' THEN CURRENT_TIMESTAMP ELSE paid_at END
                WHERE id = ? AND status IN ('PENDING', 'FAILED', 'CANCELLED')
                """, status, providerTradeNo, status, paymentId);
    }

    public int markPendingPaymentPaid(String paymentId, String providerTradeNo) {
        return jdbc.update("""
                UPDATE h5_payments
                SET status = 'PAID',
                    provider_trade_no = COALESCE(provider_trade_no, ?),
                    paid_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING'
                """, providerTradeNo, paymentId);
    }

    public void assignStorylineToPayment(String paymentId, String storylineId) {
        jdbc.update("""
                UPDATE h5_payments
                SET storyline_id = ?
                WHERE id = ? AND (storyline_id IS NULL OR storyline_id = '')
                """, storylineId, paymentId);
    }

    public void recordPaymentEvent(
            String paymentId,
            String eventType,
            String status,
            String providerTradeNo,
            String message
    ) {
        jdbc.update("""
                INSERT INTO h5_payment_events (payment_id, event_type, status, provider_trade_no, message)
                VALUES (?, ?, ?, NULLIF(?, ''), ?)
                """, paymentId, eventType, status, safe(providerTradeNo), safe(message));
    }

    public int debitBalanceIfEnough(long userId, BigDecimal amount) {
        return jdbc.update(
                "UPDATE app_users SET balance = balance - ? WHERE id = ? AND balance >= ?",
                amount, userId, amount);
    }

    public int insertPaidOrderFromPayment(String paymentId) {
        return jdbc.update("""
                INSERT IGNORE INTO orders
                (id, user_id, drama_id, storyline_id, title, amount, payment_method, payment_method_key, status, paid_at)
                SELECT p.id, p.user_id, p.drama_id, p.storyline_id,
                       COALESCE(s.name, '支付订单'), p.amount, p.method_name, p.method_key, 'PAID', CURRENT_TIMESTAMP
                FROM h5_payments p
                LEFT JOIN storylines s ON s.id = p.storyline_id
                WHERE p.id = ? AND p.status = 'PAID'
                """, paymentId);
    }

    public void insertUnlockFromPayment(String paymentId) {
        jdbc.update("""
                INSERT IGNORE INTO user_unlocks (user_id, drama_id, storyline_id, order_id)
                SELECT user_id, drama_id, storyline_id, id
                FROM h5_payments
                WHERE id = ? AND status = 'PAID' AND storyline_id IS NOT NULL AND storyline_id <> ''
                """, paymentId);
    }

    public void addPaidAmountFromPayment(String paymentId) {
        jdbc.update("""
                UPDATE app_users u
                JOIN h5_payments p ON p.user_id = u.id
                SET u.paid_amount = u.paid_amount + p.amount
                WHERE p.id = ?
                """, paymentId);
    }

    private int count(String sql, long userId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, userId);
        return value == null ? 0 : value;
    }

    private boolean exists(String sql, long userId, Object targetId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, userId, targetId);
        return value != null && value > 0;
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record RechargeRecord(
            String id,
            BigDecimal amount,
            String methodKey,
            String methodName,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record PaymentRecord(
            String id,
            long userId,
            long dramaId,
            String storylineId,
            BigDecimal amount,
            String methodKey,
            String status
    ) {
    }

    public record PaymentSummary(
            String id,
            String status,
            BigDecimal amount,
            String methodKey,
            String methodName
    ) {
    }
}
