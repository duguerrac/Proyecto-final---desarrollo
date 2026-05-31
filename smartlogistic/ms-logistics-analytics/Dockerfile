FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY ms-logistics-analytics/pom.xml .
RUN mvn dependency:go-offline -B -q
COPY ms-logistics-analytics/src ./src
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
