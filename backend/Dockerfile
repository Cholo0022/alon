FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN mkdir data && chown 10001:10001 data
COPY --from=build /build/target/alon-api-0.1.0-SNAPSHOT.jar /app/alon.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/alon.jar"]

FROM node:24-bookworm-slim AS provisioning-web
WORKDIR /build
COPY --from=frontend / ./
RUN npm ci && npm run build

FROM runtime AS provisioning
COPY --from=provisioning-web /build/dist /app/frontend
