package com.shortvideo.backend.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DataProtectionService {

    private static final String PREFIX = "enc:v1:";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;

    public DataProtectionService(@Value("${app.security.data-encryption-key}") String encryptionKey) {
        this.keySpec = new SecretKeySpec(sha256(clean(encryptionKey)), "AES");
    }

    public String encryptOrNull(String value) {
        String text = clean(value);
        if (text.isBlank()) {
            return null;
        }
        return encrypt(text);
    }

    public String encryptOrEmpty(String value) {
        String text = clean(value);
        if (text.isBlank()) {
            return "";
        }
        return encrypt(text);
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? null : "";
        }
        String text = value.trim();
        if (!isEncrypted(text)) {
            return text;
        }

        try {
            byte[] packed = Base64.getDecoder().decode(text.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Sensitive data cannot be decrypted with the configured key", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.trim().startsWith(PREFIX);
    }

    public String phoneFingerprint(String phone) {
        return fingerprint(clean(phone));
    }

    public String emailFingerprint(String email) {
        return fingerprint(clean(email).toLowerCase(Locale.ROOT));
    }

    private String encrypt(String text) {
        if (isEncrypted(text)) {
            return text;
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(iv.length + encrypted.length);
            packed.put(iv);
            packed.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(packed.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Sensitive data encryption failed", ex);
        }
    }

    private String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return HexFormat.of().formatHex(sha256(value));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
