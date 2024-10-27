# build step
FROM maven:3.9.9-eclipse-temurin-21-jammy AS build
COPY pom.xml /build/
COPY src /build/src/
WORKDIR /build/
RUN mvn clean package

# release step
FROM bellsoft/liberica-runtime-container:jdk-21-slim-musl AS release
COPY --from=builder /build/target/*.jar /app.jar
WORKDIR /app
VOLUME /app/uploadfiles

ENTRYPOINT ["java", "-jar", "app.jar"]
