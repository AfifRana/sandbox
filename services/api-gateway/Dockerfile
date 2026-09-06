# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/order-service/pom.xml services/order-service/
COPY services/product-service/pom.xml services/product-service/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/api-gateway/pom.xml services/api-gateway/
RUN mvn -q -pl services/api-gateway -am dependency:go-offline
COPY services/api-gateway services/api-gateway
RUN mvn -q -pl services/api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system app && adduser --system app --ingroup app
USER app
COPY --from=build /workspace/services/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
