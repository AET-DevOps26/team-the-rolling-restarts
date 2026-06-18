FROM gradle:jdk-25-and-25 AS build

WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY generated ./generated
COPY user-service ./user-service

RUN chmod +x ./gradlew
RUN ./gradlew :user-service:bootJar --no-daemon

FROM gcr.io/distroless/java25-debian13

WORKDIR /app

COPY --from=build /app/user-service/build/libs/*.jar app.jar

EXPOSE 8081

CMD ["-jar", "/app/app.jar"]
