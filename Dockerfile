FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

RUN chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /data/short-video/uploads/avatars /data/short-video/logs \
    && chown -R app:app /app /data/short-video

COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8081/api/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
