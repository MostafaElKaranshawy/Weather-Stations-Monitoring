FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY weather-station/pom.xml weather-station/
COPY kafka-stream/pom.xml kafka-stream/
COPY central-station/pom.xml central-station/
COPY elasticsearch-client/pom.xml elasticsearch-client/
COPY open-meteo-adapter/pom.xml open-meteo-adapter/

RUN mvn -pl kafka-stream -am dependency:go-offline

COPY kafka-stream/src ./kafka-stream/src
RUN mvn -pl kafka-stream -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/kafka-stream/target/kafka-stream-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
