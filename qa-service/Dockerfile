# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY libs/gradle-8.14-bin.zip libs/
COPY build.gradle.kts settings.gradle.kts ./

COPY shared shared
COPY qa-service qa-service

RUN chmod +x gradlew \
    && ./gradlew :qa-service:bootJar -x test --no-daemon

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY libs/opentelemetry-javaagent.jar /opt/opentelemetry-javaagent.jar
RUN chmod 644 /opt/opentelemetry-javaagent.jar

COPY --from=build /workspace/qa-service/build/libs/*.jar app.jar
RUN chown -R app:app /app

USER app

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/opentelemetry-javaagent.jar"
ENV OTEL_SERVICE_NAME="qa-service"
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector:4317"
ENV OTEL_TRACES_EXPORTER="otlp"
ENV OTEL_METRICS_EXPORTER="otlp"
ENV OTEL_LOGS_EXPORTER="otlp"

EXPOSE 9100

ENTRYPOINT ["java", "-jar", "app.jar"]
