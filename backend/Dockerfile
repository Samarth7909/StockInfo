# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B --no-transfer-progress
COPY src/ src/
RUN ./mvnw package -DskipTests -B --no-transfer-progress

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN mkdir -p /data && addgroup -S appgroup && adduser -S appuser -G appgroup \
    && chown -R appuser:appgroup /app /data
USER appuser
COPY --from=build /app/target/ops-console-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
