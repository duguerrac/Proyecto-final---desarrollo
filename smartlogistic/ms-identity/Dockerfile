FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/ms-identity-1.0.0.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
