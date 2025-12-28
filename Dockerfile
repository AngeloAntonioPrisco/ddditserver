FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Copy only pom.xml file
COPY pom.xml .

# 2. Download all dependencies
# 'go-offline' command download all without compile
RUN mvn dependency:go-offline -B

# 3. Copy source code
COPY src ./src

# 4. Compile
# ATTENTION: for now it skips tests with 'DskipTests'
RUN mvn clean package -DskipTests

# 5. Run the application
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]