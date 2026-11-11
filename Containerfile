FROM --platform=$BUILDPLATFORM registry.access.redhat.com/hi/openjdk:latest-builder AS build
USER 0
RUN dnf install -y maven3.9 && dnf clean all
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM registry.access.redhat.com/hi/openjdk:25
USER 0
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/quarkus-app/ ./
# Quarkus reads /app/config/application.properties. The directory has to exist
# so a read-only root can mount a ConfigMap file there.
RUN mkdir -p /app/config && chown 10001:10001 /app/config
ENV HOME=/tmp
USER 10001
EXPOSE 8080
ENTRYPOINT ["/usr/lib/jvm/java-25-openjdk/bin/java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=75.0", "-jar", "quarkus-run.jar"]
CMD []
