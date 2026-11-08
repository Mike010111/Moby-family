# syntax=docker/dockerfile:1.7
# Один Dockerfile для всех сервисов. Стадия build одинакова для всех (ARG SERVICE используется только
# в runtime), поэтому `docker compose build` собирает jar-ы один раз и переиспользует слой.

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY gradlew gradle.properties settings.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
COPY libs libs
COPY services services
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q bootJar

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE
ARG PORT
ENV SERVICE_PORT=${PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Europe/Minsk"
RUN addgroup -S moby && adduser -S moby -G moby
WORKDIR /app
COPY --from=build /workspace/services/${SERVICE}/build/libs/app.jar app.jar
USER moby
EXPOSE ${PORT}
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=12 \
  CMD wget -qO- "http://127.0.0.1:${SERVICE_PORT}/actuator/health/readiness" | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
