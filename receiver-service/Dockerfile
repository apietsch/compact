FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY sender-service/pom.xml sender-service/pom.xml
COPY receiver-service/pom.xml receiver-service/pom.xml
COPY receiver-service/src receiver-service/src

RUN mvn -pl receiver-service -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/receiver-service/target/receiver-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
