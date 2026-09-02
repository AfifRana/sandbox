# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/order-service/pom.xml services/order-service/
RUN mvn -q -pl services/order-service -am dependency:go-offline
COPY services/order-service services/order-service
RUN mvn -q -pl services/order-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system app && adduser --system app --ingroup app
USER app
COPY --from=build /workspace/services/order-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
