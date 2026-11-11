FROM docker.io/maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM registry.access.redhat.com/ubi10/openjdk-25-runtime:1790254155
WORKDIR /work/
COPY --from=builder /app/target/quarkus-app/lib/ /work/lib/
COPY --from=builder /app/target/quarkus-app/*.jar /work/
COPY --from=builder /app/target/quarkus-app/app/ /work/app/
COPY --from=builder /app/target/quarkus-app/quarkus/ /work/quarkus/
EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0"
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
