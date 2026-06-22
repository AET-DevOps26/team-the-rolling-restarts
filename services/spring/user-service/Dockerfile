FROM gradle:jdk-25-and-25 AS deps

WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY generated/build.gradle ./generated/build.gradle
COPY user-service/build.gradle ./user-service/build.gradle

# Download dependencies (cached unless build.gradle changes)
RUN ./gradlew :user-service:dependencies --no-daemon -q

FROM deps AS build

COPY generated ./generated
COPY user-service ./user-service

RUN ./gradlew :user-service:bootJar --no-daemon

FROM gcr.io/distroless/java25-debian13

WORKDIR /app

COPY --from=busybox:stable-musl /bin/busybox /usr/bin/wget
COPY --from=build /app/user-service/build/libs/*.jar app.jar

EXPOSE 8081

CMD ["-jar", "/app/app.jar"]
