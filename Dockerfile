FROM node:22-alpine AS frontend-build

WORKDIR /frontend

COPY src/frontend/package.json src/frontend/package-lock.json ./
RUN npm ci

COPY src/frontend ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS build

ARG APP_VERSION=0.0.1-SNAPSHOT
ENV APP_VERSION=${APP_VERSION}

WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew build.gradle settings.gradle ./
COPY src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static

RUN chmod +x gradlew \
    && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine AS runtime

ARG APP_VERSION=0.0.1-SNAPSHOT
ARG VCS_REF=unknown

LABEL org.opencontainers.image.title="Fraud Rule Engine" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S app -G app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/build/libs/*.jar app.jar

USER app

EXPOSE 8080 8081

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD ["curl", "--fail", "--silent", "--show-error", "http://localhost:8080/livez"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
