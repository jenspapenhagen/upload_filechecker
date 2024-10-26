# build step
FROM maven:3.9.9-eclipse-temurin-21-jammy AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
USER root
RUN chown -R builder /usr/src/app
USER builder
RUN mvn -f /usr/src/app/pom.xml clean package

# release step
FROM bellsoft/liberica-runtime-container:jdk-21-slim-musl AS release
COPY --from=builder /job/app/build/libs/*.jar /app.jar
WORKDIR /app
VOLUME /app/data

ENTRYPOINT ["java", "-jar", "/app.jar"]