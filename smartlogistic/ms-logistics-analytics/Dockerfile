# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY ms-logistics-analytics/pom.xml .
RUN mvn dependency:go-offline -B
COPY ms-logistics-analytics/src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime with JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
