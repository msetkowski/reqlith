# syntax=docker/dockerfile:1
# Build context: repository root (docker build -f src/main/docker/Dockerfile .)
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -Djacoco.skip=true package \
    && cp target/reqlith-*.jar /app.jar
# Split the Spring Boot jar into layers (dependencies change rarely, the application often):
# each layer holds part of app.jar + lib/, whose manifest class path ties them together again.
RUN java -Djarmode=tools -jar /app.jar extract --layers --destination /layers

FROM docker.io/library/eclipse-temurin:25-jre
WORKDIR /opt/reqlith
# Least frequently changing layers first, so a code change only rebuilds the last small layer.
COPY --from=build /layers/dependencies/ ./
# Headless Chromium for PDF export, installed with the Playwright version from the dependencies.
# This layer is rebuilt only when dependencies change.
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright
RUN java -cp "lib/*" com.microsoft.playwright.CLI install --with-deps --only-shell chromium \
    && rm -rf /var/lib/apt/lists/* /tmp/playwright-java-* \
    && useradd --system --uid 10001 reqlith
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER reqlith
EXPOSE 8080
# Kubernetes: livenessProbe /actuator/health/liveness, readinessProbe /actuator/health/readiness (port 8080).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/reqlith/app.jar"]
