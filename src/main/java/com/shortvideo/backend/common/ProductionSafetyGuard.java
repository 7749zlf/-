package com.shortvideo.backend.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionSafetyGuard {

    private static final String DEFAULT_DB_PASSWORD = "ShortVideo@123456";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123456";
    private static final String DEFAULT_DATA_ENCRYPTION_KEY = "short-video-dev-data-key";

    private final Environment environment;
    private final boolean productionMode;
    private final String datasourcePassword;
    private final String bootstrapAdminPassword;
    private final String legacyAdminToken;
    private final String paymentCallbackToken;
    private final String dataEncryptionKey;
    private final String allowedOrigins;
    private final String publicBaseUrl;
    private final boolean demoInstantUnlockEnabled;
    private final boolean demoRechargeEnabled;
    private final boolean demoPaymentEnabled;

    public ProductionSafetyGuard(
            Environment environment,
            @Value("${app.security.production-mode:false}") boolean productionMode,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${app.security.bootstrap-admin-password:}") String bootstrapAdminPassword,
            @Value("${app.security.admin-token:}") String legacyAdminToken,
            @Value("${app.security.payment-callback-token:}") String paymentCallbackToken,
            @Value("${app.security.data-encryption-key:}") String dataEncryptionKey,
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.public-base-url:}") String publicBaseUrl,
            @Value("${app.demo.instant-unlock-enabled:true}") boolean demoInstantUnlockEnabled,
            @Value("${app.demo.recharge-enabled:true}") boolean demoRechargeEnabled,
            @Value("${app.demo.payment-enabled:true}") boolean demoPaymentEnabled
    ) {
        this.environment = environment;
        this.productionMode = productionMode;
        this.datasourcePassword = clean(datasourcePassword);
        this.bootstrapAdminPassword = clean(bootstrapAdminPassword);
        this.legacyAdminToken = clean(legacyAdminToken);
        this.paymentCallbackToken = clean(paymentCallbackToken);
        this.dataEncryptionKey = clean(dataEncryptionKey);
        this.allowedOrigins = clean(allowedOrigins);
        this.publicBaseUrl = clean(publicBaseUrl);
        this.demoInstantUnlockEnabled = demoInstantUnlockEnabled;
        this.demoRechargeEnabled = demoRechargeEnabled;
        this.demoPaymentEnabled = demoPaymentEnabled;
    }

    @PostConstruct
    void verifyProductionSafety() {
        if (!isProduction()) {
            return;
        }

        List<String> problems = new ArrayList<>();
        if (datasourcePassword.isBlank() || DEFAULT_DB_PASSWORD.equals(datasourcePassword)) {
            problems.add("SHORT_VIDEO_DB_PASSWORD must be set to a non-default value");
        }
        if (bootstrapAdminPassword.isBlank() || DEFAULT_ADMIN_PASSWORD.equals(bootstrapAdminPassword)
                || bootstrapAdminPassword.length() < 12) {
            problems.add("SHORT_VIDEO_BOOTSTRAP_ADMIN_PASSWORD must be non-default and at least 12 characters");
        }
        if (paymentCallbackToken.length() < 32) {
            problems.add("SHORT_VIDEO_PAYMENT_CALLBACK_TOKEN must be at least 32 characters");
        }
        if (!legacyAdminToken.isBlank() && legacyAdminToken.length() < 32) {
            problems.add("SHORT_VIDEO_ADMIN_TOKEN must be blank or at least 32 characters");
        }
        if (dataEncryptionKey.isBlank()
                || DEFAULT_DATA_ENCRYPTION_KEY.equals(dataEncryptionKey)
                || dataEncryptionKey.length() < 32) {
            problems.add("SHORT_VIDEO_DATA_ENCRYPTION_KEY must be non-default and at least 32 characters");
        }
        if (allowedOrigins.isBlank() || containsLocalOrigin(allowedOrigins)) {
            problems.add("SHORT_VIDEO_CORS_ORIGINS must not contain local or wildcard origins");
        }
        if (publicBaseUrl.isBlank()
                || !publicBaseUrl.toLowerCase(Locale.ROOT).startsWith("https://")
                || containsLocalOrigin(publicBaseUrl)) {
            problems.add("SHORT_VIDEO_PUBLIC_BASE_URL must be an HTTPS public domain");
        }
        if (demoInstantUnlockEnabled || demoRechargeEnabled || demoPaymentEnabled) {
            problems.add("SHORT_VIDEO_DEMO_* switches must be disabled in production");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Production safety check failed: " + String.join("; ", problems));
        }
    }

    private boolean isProduction() {
        return productionMode || Arrays.stream(environment.getActiveProfiles())
                .anyMatch((profile) -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
    }

    private boolean containsLocalOrigin(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        return text.contains("*")
                || text.contains("localhost")
                || text.contains("127.0.0.1")
                || text.contains("0.0.0.0")
                || text.contains("192.168.")
                || text.contains("10.")
                || text.contains("172.16.");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
