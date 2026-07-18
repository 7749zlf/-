package com.shortvideo.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CorsConfigTests {

    @Test
    void trimsDeduplicatesAndDropsBlankOrigins() {
        CorsConfig config = new CorsConfig(" https://h5.example.com, ,https://h5.example.com,https://admin.example.com ");

        assertThat((String[]) ReflectionTestUtils.getField(config, "allowedOrigins"))
                .containsExactly("https://h5.example.com", "https://admin.example.com");
    }

    @Test
    void rejectsBlankOriginList() {
        assertThatThrownBy(() -> new CorsConfig(" , "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.cors.allowed-origins");
    }
}
