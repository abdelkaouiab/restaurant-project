# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine-3.24 AS runtime

RUN addgroup -S -g 10001 restaurant \
    && adduser -S -D -H -u 10001 -G restaurant restaurant \
    && mkdir -p /opt/restaurant /var/lib/restaurant/product-images \
    && chown -R restaurant:restaurant /opt/restaurant /var/lib/restaurant

WORKDIR /opt/restaurant

COPY --from=build --chown=restaurant:restaurant \
    /workspace/target.nosync/restaurant-backend-*.jar ./application.jar

USER 10001:10001

EXPOSE 8080 8081

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"

ENTRYPOINT ["java", "-jar", "/opt/restaurant/application.jar"]
