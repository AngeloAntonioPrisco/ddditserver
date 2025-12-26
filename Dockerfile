# Use OpenJDK 17 (same version on the server)
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/ddditserver-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Execute service in background
ENTRYPOINT ["java","-jar","app.jar"]