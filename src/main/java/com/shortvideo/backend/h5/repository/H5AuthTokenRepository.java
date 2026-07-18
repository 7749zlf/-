package com.shortvideo.backend.h5.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class H5AuthTokenRepository {

    private final JdbcTemplate jdbc;

    public H5AuthTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findActiveUserIdByRefreshToken(String refreshToken) {
        return jdbc.query("""
                SELECT user_id
                FROM h5_auth_tokens
                WHERE refresh_token = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong("user_id"), refreshToken).stream().findFirst();
    }

    public Optional<Long> findActiveUserIdByAccessToken(String token) {
        return jdbc.query("""
                SELECT user_id
                FROM h5_auth_tokens
                WHERE token = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong("user_id"), token).stream().findFirst();
    }

    public void save(String token, String refreshToken, long userId, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO h5_auth_tokens (token, refresh_token, user_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, token, refreshToken, userId, Timestamp.valueOf(expiresAt));
    }

    public void revokeByRefreshToken(String refreshToken) {
        jdbc.update("UPDATE h5_auth_tokens SET revoked = TRUE WHERE refresh_token = ?", refreshToken);
    }
}
