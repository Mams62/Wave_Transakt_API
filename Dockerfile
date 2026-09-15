# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package && JAR="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)" && test -n "$JAR" && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system wave && useradd --system --gid wave --home-dir /app wave
COPY --from=build --chown=wave:wave /workspace/app.jar /app/app.jar
USER wave
ENV PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
