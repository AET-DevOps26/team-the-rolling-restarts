FROM gradle:jdk-25-and-25 AS build

WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY generated ./generated
COPY api-gateway ./api-gateway

RUN chmod +x ./gradlew
RUN ./gradlew :api-gateway:bootJar --no-daemon

FROM gcr.io/distroless/java25-debian13

WORKDIR /app

COPY --from=build /app/api-gateway/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["-jar", "/app/app.jar"]
