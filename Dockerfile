FROM eclipse-temurin:21-jdk-alpine
RUN apk add --no-cache curl unzip
RUN addgroup -S agentic && adduser -S -G agentic -u 10001 agentic
WORKDIR /app
COPY target/agentic-sdlc-orchestrator-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/workspaces && chown -R agentic:agentic /app
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
