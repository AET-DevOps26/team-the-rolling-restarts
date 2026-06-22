FROM gradle:jdk-25-and-25 AS deps

WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY generated/build.gradle ./generated/build.gradle
COPY api-gateway/build.gradle ./api-gateway/build.gradle

# Download dependencies (cached unless build.gradle changes)
RUN ./gradlew :api-gateway:dependencies --no-daemon -q

FROM deps AS build

COPY generated ./generated
COPY api-gateway ./api-gateway

RUN ./gradlew :api-gateway:bootJar --no-daemon

FROM gcr.io/distroless/java25-debian13

WORKDIR /app

COPY --from=busybox:stable-musl /bin/busybox /usr/bin/wget
COPY --from=build /app/api-gateway/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["-jar", "/app/app.jar"]
