package com.shortvideo.backend.common;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserSensitiveDataBackfill {

    private final JdbcTemplate jdbc;
    private final DataProtectionService dataProtection;

    public UserSensitiveDataBackfill(JdbcTemplate jdbc, DataProtectionService dataProtection) {
        this.jdbc = jdbc;
        this.dataProtection = dataProtection;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void encryptExistingUserData() {
        List<UserSensitiveRow> rows = jdbc.query("""
                SELECT id, phone, phone_hash, email, email_hash
                FROM app_users
                """, (rs, rowNum) -> new UserSensitiveRow(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("phone_hash"),
                rs.getString("email"),
                rs.getString("email_hash")
        ));

        for (UserSensitiveRow row : rows) {
            String phone = dataProtection.decrypt(row.phone());
            String email = dataProtection.decrypt(row.email());
            String phoneHash = nullableHash(dataProtection.phoneFingerprint(phone));
            String emailHash = nullableHash(dataProtection.emailFingerprint(email));
            String protectedPhone = dataProtection.encryptOrNull(phone);
            String protectedEmail = dataProtection.encryptOrEmpty(email);

            if (same(row.phone(), protectedPhone)
                    && same(row.email(), protectedEmail)
                    && same(row.phoneHash(), phoneHash)
                    && same(row.emailHash(), emailHash)) {
                continue;
            }

            jdbc.update("""
                    UPDATE app_users
                    SET phone = ?, phone_hash = ?, email = ?, email_hash = ?
                    WHERE id = ?
                    """, protectedPhone, phoneHash, protectedEmail, emailHash, row.id());
        }
    }

    private String nullableHash(String hash) {
        return hash == null || hash.isBlank() ? null : hash;
    }

    private boolean same(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return a.equals(b);
    }

    private record UserSensitiveRow(
            long id,
            String phone,
            String phoneHash,
            String email,
            String emailHash
    ) {
    }
}
