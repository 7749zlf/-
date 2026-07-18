package com.shortvideo.backend.common;

import java.nio.charset.StandardCharsets;

public final class TextEncodingRepair {

    private static final int MAX_REPAIR_PASSES = 4;

    private TextEncodingRepair() {
    }

    public static String repair(String value) {
        if (value == null || mojibakeScore(value) == 0) {
            return value;
        }

        String current = value;
        int currentScore = mojibakeScore(current);

        for (int i = 0; i < MAX_REPAIR_PASSES; i++) {
            String decoded = decodeLatin1AsUtf8(current);
            if (decoded.equals(current)) {
                break;
            }

            int decodedScore = mojibakeScore(decoded);
            if (decodedScore > currentScore) {
                break;
            }

            current = decoded;
            currentScore = decodedScore;
            if (currentScore == 0) {
                break;
            }
        }

        return current;
    }

    private static String decodeLatin1AsUtf8(String value) {
        if (value.chars().anyMatch(character -> character > 255)) {
            return value;
        }
        return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private static int mojibakeScore(String value) {
        int score = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\uFFFD') {
                score += 5;
            } else if (character == 'Ã' || character == 'Â' || character == 'â') {
                score += 2;
            } else if (character >= 0x80 && character <= 0x9F) {
                score += 2;
            }
        }
        return score;
    }
}
