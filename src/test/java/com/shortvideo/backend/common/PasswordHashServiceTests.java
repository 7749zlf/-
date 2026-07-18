package com.shortvideo.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class PasswordHashServiceTests {

    private final PasswordHashService service = new PasswordHashService();

    @Test
    void encodesNewPasswordsWithBcrypt() {
        PasswordHashService.EncodedPassword encoded = service.encode("Secret123!");

        assertThat(encoded.salt()).isEqualTo("bcrypt");
        assertThat(encoded.hash()).startsWith("$2");
        assertThat(service.matches(encoded.salt(), encoded.hash(), "Secret123!")).isTrue();
        assertThat(service.matches(encoded.salt(), encoded.hash(), "wrong")).isFalse();
        assertThat(service.needsUpgrade(encoded.salt(), encoded.hash())).isFalse();
    }

    @Test
    void keepsLegacySha256PasswordsReadableForMigration() throws Exception {
        String legacyHash = legacySha256("abc", "Secret123!");

        assertThat(service.matches("abc", legacyHash, "Secret123!")).isTrue();
        assertThat(service.matches("abc", legacyHash, "wrong")).isFalse();
        assertThat(service.needsUpgrade("abc", legacyHash)).isTrue();
    }

    private String legacySha256(String salt, String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8)));
    }
}
