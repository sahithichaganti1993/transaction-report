# ---- build ----------------------------------------------------------------
FROM maven:3.8-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first so a source-only change reuses the cached layer.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---- run ------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --create-home --uid 10001 appuser
COPY --from=build /build/target/transaction-report.war app.war
USER appuser

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.war"]
