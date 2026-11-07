# syntax=docker/dockerfile:1
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/reqlith-*.jar /app.jar

FROM docker.io/library/eclipse-temurin:25-jre
RUN useradd --system --uid 10001 reqlith
COPY --from=build /app.jar /opt/reqlith/app.jar
USER reqlith
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/reqlith/app.jar"]
