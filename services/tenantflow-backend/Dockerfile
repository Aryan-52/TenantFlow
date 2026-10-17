FROM eclipse-temurin:24-jdk-noble AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
# Fetch plugins/dependencies in their own layer so source changes reuse the cache.
RUN ./mvnw -B -ntp dependency:go-offline || true
COPY src ./src
RUN ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:24-jre-noble
# Run as an unprivileged, fixed-uid user; the application jar stays root-owned (read-only).
RUN groupadd --system --gid 10001 tenantflow \
    && useradd --system --uid 10001 --gid tenantflow --no-create-home --shell /usr/sbin/nologin tenantflow
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# The container listens on 8080 (nginx.conf and docker-compose.yml depend on it);
# application.properties defaults to 8095 only for local development. 8091 is the
# management port (metrics, detailed health): never publish it publicly.
ENV SERVER_PORT=8080 \
    MANAGEMENT_PORT=8091
EXPOSE 8080 8091
USER 10001:10001
# Status-only readiness probe on the public port; bash's /dev/tcp avoids needing curl.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT} && printf 'GET /readyz HTTP/1.0\r\n\r\n' >&3 && grep -q UP <&3"]
# Container-aware heap sizing; exit (and let the orchestrator restart) on OOM. SIGTERM
# triggers Spring's graceful shutdown (server.shutdown=graceful).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
