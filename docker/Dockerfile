## Multi-stage build: build with Maven, run with JRE

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

# Leverage layer caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

# Create directories for uploads and logs
RUN mkdir -p /app/uploads /app/logs

# Copy jar
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Environment defaults (can be overridden by docker-compose .env)
ENV APP_RAG_UPLOAD_DIR=/app/uploads

# Expose port
EXPOSE 8080

# Use Spring Boot property to write logs to file
CMD ["sh","-c","java -jar /app/app.jar --logging.file.name=/app/logs/app.log --app.rag.upload-dir=${APP_RAG_UPLOAD_DIR}"]


