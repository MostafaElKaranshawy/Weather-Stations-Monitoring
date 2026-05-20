# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy root pom and module poms for dependency resolution
COPY pom.xml .
COPY weather-station/pom.xml weather-station/
COPY kafka-stream/pom.xml kafka-stream/
COPY central-station/pom.xml central-station/
COPY elasticsearch-client/pom.xml elasticsearch-client/
COPY open-meteo-adapter/pom.xml open-meteo-adapter/

# Pre-download dependencies
RUN mvn -pl open-meteo-adapter -am dependency:go-offline

# Copy source code and build
COPY open-meteo-adapter/src ./open-meteo-adapter/src
RUN mvn -pl open-meteo-adapter -am clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=build /app/open-meteo-adapter/target/open-meteo-adapter-1.0-SNAPSHOT.jar app.jar

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
