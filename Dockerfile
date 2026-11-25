# Multi-stage build: Maven en broncode blijven buiten de runtime-image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/scriptopslag-poc-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 7000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
