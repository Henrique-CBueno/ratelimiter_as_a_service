# syntax=docker/dockerfile:1

# ---- Build stage: compiles the whole Maven reactor, needed since rls-bootstrap depends on every
# other module. Tests are skipped here (see design decision 5): they need Testcontainers, which
# needs a Docker daemon unavailable inside this build, and they already run via `mvn verify`
# outside Docker in normal local/CI workflows before an image would ever be built.
#
# Repackaging (spring-boot:repackage, producing the executable fat jar) runs as its own separate
# Maven invocation rather than being bound into rls-bootstrap's own build lifecycle: binding it to
# any phase that `mvn verify` also runs breaks Failsafe's `@SpringBootTest` classes when more than
# one runs together in the same suite (they stop finding `@SpringBootConfiguration` by classpath
# scanning) — the exact mechanism wasn't worth chasing further since `mvn verify` is what every
# other spec in this project relies on for correctness, so it must stay completely unaffected by
# Docker packaging concerns.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# curl is needed to download the standalone Tailwind CLI binary during rls-adapter-web's
# generate-resources phase (see rls-adapter-web/scripts/download-tailwind.sh) — Tailwind CSS is
# compiled at build time, not bundled or fetched via Node/npm.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY rls-domain/pom.xml rls-domain/pom.xml
COPY rls-application/pom.xml rls-application/pom.xml
COPY rls-adapter-redis/pom.xml rls-adapter-redis/pom.xml
COPY rls-adapter-persistence/pom.xml rls-adapter-persistence/pom.xml
COPY rls-adapter-resilience/pom.xml rls-adapter-resilience/pom.xml
COPY rls-adapter-rest/pom.xml rls-adapter-rest/pom.xml
COPY rls-adapter-web/pom.xml rls-adapter-web/pom.xml
COPY rls-bootstrap/pom.xml rls-bootstrap/pom.xml

COPY rls-domain/src rls-domain/src
COPY rls-application/src rls-application/src
COPY rls-adapter-redis/src rls-adapter-redis/src
COPY rls-adapter-persistence/src rls-adapter-persistence/src
COPY rls-adapter-resilience/src rls-adapter-resilience/src
COPY rls-adapter-rest/src rls-adapter-rest/src
COPY rls-adapter-web/src rls-adapter-web/src
COPY rls-adapter-web/scripts rls-adapter-web/scripts
COPY rls-bootstrap/src rls-bootstrap/src

RUN chmod +x rls-adapter-web/scripts/download-tailwind.sh

RUN mvn -B -q -pl rls-bootstrap -am install -DskipTests \
    && mvn -B -q -pl rls-bootstrap package org.springframework.boot:spring-boot-maven-plugin:3.5.16:repackage -DskipTests

# ---- Runtime stage: slim JRE, only the runnable jar ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/rls-bootstrap/target/rls-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
