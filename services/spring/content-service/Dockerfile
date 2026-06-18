FROM gradle:jdk-25-and-25 AS build

WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY generated ./generated
COPY content-service ./content-service

RUN chmod +x ./gradlew
RUN ./gradlew :content-service:bootJar --no-daemon

FROM gcr.io/distroless/java25-debian13

WORKDIR /app

COPY --from=busybox:stable-musl /bin/busybox /usr/bin/wget
COPY --from=build /app/content-service/build/libs/*.jar app.jar

EXPOSE 8082

CMD ["-jar", "/app/app.jar"]
