# Stage 1: Build the application
FROM gradle:jdk-25-and-25 AS build

WORKDIR /app

# Copy gradle configuration and sources
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY src ./src

# Build the application using the project's Gradle wrapper
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# Stage 2: Run the application
FROM gcr.io/distroless/java25-debian13

WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# The distroless Java base image already sets `java` as the entrypoint.
# Provide only the java arguments as the image `CMD` so the final invocation is `java -jar /app/app.jar`.
CMD ["-jar","/app/app.jar"]
