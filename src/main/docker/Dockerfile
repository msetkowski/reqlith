# syntax=docker/dockerfile:1
# Build context: repository root (docker build -f src/main/docker/Dockerfile .)
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/reqlith-*.jar /app.jar

FROM docker.io/library/eclipse-temurin:25-jre
COPY --from=build /app.jar /opt/reqlith/app.jar
# Headless Chromium for PDF export, installed with the Playwright version bundled in the app.
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright
RUN java -Dloader.main=com.microsoft.playwright.CLI -cp /opt/reqlith/app.jar \
        org.springframework.boot.loader.launch.PropertiesLauncher install --with-deps --only-shell chromium \
    && rm -rf /var/lib/apt/lists/* /tmp/playwright-java-* \
    && useradd --system --uid 10001 reqlith
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
USER reqlith
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/reqlith/app.jar"]
