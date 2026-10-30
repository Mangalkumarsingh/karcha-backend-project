
# Build Spring Boot application
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src ./src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon bootJar

# Run Spring Boot application
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
