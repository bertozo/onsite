# syntax=docker/dockerfile:1

# ---- build ----
# Matches the project's jvmToolchain(21); the Gradle wrapper pins the exact Gradle version
# (see gradle/wrapper/gradle-wrapper.properties) so this stays reproducible without needing
# a base image tagged to a specific Gradle release.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first so `docker build` can cache them across source-only changes.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
# The executable bit on gradlew often doesn't survive a Windows host's COPY into the image.
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon buildFatJar

# ---- runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/onsite-backend-all.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
