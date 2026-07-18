package com.shortvideo.backend.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordHashService {

    private static final String BCRYPT_MARKER = "bcrypt";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public EncodedPassword encode(String password) {
        return new EncodedPassword(BCRYPT_MARKER, encoder.encode(clean(password)));
    }

    public boolean matches(String salt, String expectedHash, String password) {
        if (expectedHash == null || expectedHash.isBlank() || password == null) {
            return false;
        }
        if (isBcrypt(salt, expectedHash)) {
            try {
                return encoder.matches(clean(password), expectedHash);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        if (salt == null || salt.isBlank()) {
            return false;
        }
        return constantTimeEquals(expectedHash, legacySha256(salt, password));
    }

    public boolean needsUpgrade(String salt, String expectedHash) {
        return expectedHash != null && !expectedHash.isBlank() && !isBcrypt(salt, expectedHash);
    }

    private boolean isBcrypt(String salt, String expectedHash) {
        return BCRYPT_MARKER.equalsIgnoreCase(clean(salt))
                || expectedHash.startsWith("$2a$")
                || expectedHash.startsWith("$2b$")
                || expectedHash.startsWith("$2y$");
    }

    private String legacySha256(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + ":" + clean(password)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8),
                actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record EncodedPassword(String salt, String hash) {
    }
}
