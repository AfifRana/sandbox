# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/order-service/pom.xml services/order-service/
COPY services/product-service/pom.xml services/product-service/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/notification-service/pom.xml services/notification-service/
RUN mvn -q -pl services/notification-service -am dependency:go-offline
COPY services/notification-service services/notification-service
RUN mvn -q -pl services/notification-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system app && adduser --system app --ingroup app
USER app
COPY --from=build /workspace/services/notification-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
