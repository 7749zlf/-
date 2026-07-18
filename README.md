# Short Video Backend

Spring Boot backend for the short-video application.

## Production Profile

Production deployments must run with:

```text
SPRING_PROFILES_ACTIVE=prod
SHORT_VIDEO_PRODUCTION_MODE=true
```

Required environment variables:

```text
SHORT_VIDEO_DB_URL
SHORT_VIDEO_DB_USERNAME
SHORT_VIDEO_DB_PASSWORD
SHORT_VIDEO_PUBLIC_BASE_URL
SHORT_VIDEO_CORS_ORIGINS
SHORT_VIDEO_AVATAR_UPLOAD_DIR
SHORT_VIDEO_BOOTSTRAP_ADMIN_USERNAME
SHORT_VIDEO_BOOTSTRAP_ADMIN_PASSWORD
SHORT_VIDEO_PAYMENT_CALLBACK_TOKEN
SHORT_VIDEO_DATA_ENCRYPTION_KEY
```

Production demo switches must be disabled:

```text
SHORT_VIDEO_DEMO_INSTANT_UNLOCK_ENABLED=false
SHORT_VIDEO_DEMO_RECHARGE_ENABLED=false
SHORT_VIDEO_DEMO_PAYMENT_ENABLED=false
```

## Health Checks

Use `/api/health/live` for process liveness.

Use `/api/health/ready` for readiness. The readiness check verifies database connectivity and upload storage writability.

## Build

```bash
./mvnw -DskipTests package
```

## Focused Tests

```bash
./mvnw "-Dtest=CorsConfigTests,ProductionSafetyGuardTests,PasswordHashServiceTests,TextEncodingRepairTests,H5UserServicePaymentStatusTests,H5UserServiceProductionModeTests,H5UserServiceSmsRateLimitTests" test
```

The full integration suite requires a reachable MySQL database with the project schema and seed data.
