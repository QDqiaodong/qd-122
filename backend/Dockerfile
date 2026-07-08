ARG DOCKER_REGISTRY

FROM ${DOCKER_REGISTRY}/library/maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn compile -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -B

COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

FROM ${DOCKER_REGISTRY}/library/eclipse-temurin:17-jre-jammy AS production
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
