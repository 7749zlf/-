package com.shortvideo.backend.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyGuardTests {

    @Test
    void rejectsShortLegacyAdminTokenInProduction() {
        ProductionSafetyGuard guard = guard("short-token");

        assertThatThrownBy(guard::verifyProductionSafety)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHORT_VIDEO_ADMIN_TOKEN");
    }

    @Test
    void acceptsBlankLegacyAdminTokenInProduction() {
        ProductionSafetyGuard guard = guard("");

        assertThatCode(guard::verifyProductionSafety)
                .doesNotThrowAnyException();
    }

    private ProductionSafetyGuard guard(String legacyAdminToken) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return new ProductionSafetyGuard(
                environment,
                false,
                "NonDefaultDbPassword123!",
                "NonDefaultAdminPassword123!",
                legacyAdminToken,
                "payment-callback-token-32-characters",
                "data-encryption-key-with-32-characters",
                "https://h5.example.com,https://admin.example.com",
                "https://api.example.com",
                false,
                false,
                false
        );
    }
}
