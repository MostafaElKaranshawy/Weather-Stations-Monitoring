FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY weather-station/pom.xml weather-station/
COPY kafka-stream/pom.xml kafka-stream/
COPY central-station/pom.xml central-station/
COPY elasticsearch-client/pom.xml elasticsearch-client/
COPY open-meteo-adapter/pom.xml open-meteo-adapter/

RUN mvn -pl open-meteo-adapter -am dependency:go-offline

COPY open-meteo-adapter/src ./open-meteo-adapter/src
RUN mvn -pl open-meteo-adapter -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/open-meteo-adapter/target/open-meteo-adapter-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
