FROM gradle:jdk-25-and-25 AS deps

WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY user-service/build.gradle ./user-service/build.gradle

# Download dependencies (cached unless build.gradle changes)
RUN ./gradlew :user-service:dependencies --no-daemon -q

FROM deps AS build

COPY user-service ./user-service

RUN ./gradlew :user-service:bootJar --no-daemon

# :nonroot (not the bare default variant, which runs as root — confirmed live via a plain
# build, Spring Boot's own startup log reported "started by root") + digest-pinned, since
# distroless doesn't publish semver tags for hadolint DL3006 to be satisfied by a tag alone.
FROM gcr.io/distroless/java25-debian13:nonroot@sha256:9ccf2b8bce700d9f450523e2055afabe4d4ee795e6d69a0647a2dcd6e180e411

WORKDIR /app

COPY --from=busybox:stable-musl /bin/busybox /usr/bin/wget
COPY --from=build /app/user-service/build/libs/*.jar app.jar

EXPOSE 8081

CMD ["-jar", "/app/app.jar"]
