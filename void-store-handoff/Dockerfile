# One image: the React storefront + admin are built, then packaged into the Spring Boot jar,
# which serves the API, the pages (with server-side SEO tags) and the static files.

# ---- 1. frontend
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. backend (with the built frontend inside)
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package && cp target/void-api-*.jar /app.jar

# ---- 3. runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S void && adduser -S void -G void && mkdir -p /data/uploads && chown -R void:void /data
USER void
WORKDIR /app
COPY --from=api /app.jar ./app.jar
ENV UPLOADS_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC" \
    TZ=Africa/Cairo
VOLUME ["/data/uploads"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
