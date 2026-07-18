package com.shortvideo.backend.common;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextEncodingRepairTests {

    @Test
    void repairsRepeatedLatin1Utf8Mojibake() {
        assertEquals("掌心风暴", TextEncodingRepair.repair(mojibake("掌心风暴", 3)));
        assertEquals("246.8 万", TextEncodingRepair.repair(mojibake("246.8 万", 3)));
    }

    @Test
    void leavesReadableTextUntouched() {
        assertEquals("播放", TextEncodingRepair.repair("播放"));
        assertEquals("Sample title", TextEncodingRepair.repair("Sample title"));
        assertNull(TextEncodingRepair.repair(null));
    }

    private String mojibake(String value, int passes) {
        String current = value;
        for (int i = 0; i < passes; i++) {
            current = new String(current.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        }
        return current;
    }
}
