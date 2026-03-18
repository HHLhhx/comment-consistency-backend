FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app

# Use a non-root user in container runtime.
RUN useradd --create-home --shell /usr/sbin/nologin spring

COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8080
USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
