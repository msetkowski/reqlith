# Application image: Alpine + JRE 25, no builder stage.
# Maven builds everything into target/ first; this file only copies the results:
#   ./mvnw -DskipTests package            (or: mvnd -DskipTests package)
#   docker build -f src/main/docker/Dockerfile .
# or simply: src/main/dev/scripts/build-image.sh
FROM docker.io/library/alpine:3.24.4

# The project targets Java 25; fail the build if the package delivers anything else.
RUN apk add --no-cache openjdk25-jre-headless \
    && java -version 2>&1 | grep -q 'version "25' \
    && addgroup -S reqlith \
    && adduser -S -D -H -u 10001 -G reqlith reqlith

WORKDIR /opt/reqlith
# Dependencies change rarely, the application often: separate layers, dependencies first.
COPY target/docker/lib/ lib/
COPY target/classes/ classes/

USER reqlith
# 8080: application, 8081: actuator (Kubernetes probes, Prometheus) for cluster-internal use.
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/opt/reqlith/classes:/opt/reqlith/lib/*", "io.reqlith.ReqlithApplicationKt"]
