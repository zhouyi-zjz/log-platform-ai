FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml .
COPY log-platform-ai-common/pom.xml log-platform-ai-common/pom.xml
COPY log-platform-ai-admin/pom.xml log-platform-ai-admin/pom.xml
RUN mvn -B -q -pl log-platform-ai-admin -am dependency:go-offline

COPY log-platform-ai-common/src log-platform-ai-common/src
COPY log-platform-ai-admin/src log-platform-ai-admin/src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/log-platform-ai-admin/target/log-platform-ai-admin-1.0.0.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8103
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
