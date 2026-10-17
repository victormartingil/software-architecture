FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -U -Dmaven.wagon.http.retryHandler.count=3 -DskipTests package
FROM eclipse-temurin:21.0.12.1_1-jre-jammy
RUN groupadd -g 10001 app && useradd -u 10001 -g app -m app && mkdir /state && chown app:app /state
WORKDIR /app
COPY --from=build /build/target/order-analytics-1.0.0.jar app.jar
USER 10001:10001
ENV STATE_DIR=/state JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxDirectMemorySize=128m"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s CMD test -f /tmp/analytics-ready
ENTRYPOINT ["java","-jar","app.jar"]
