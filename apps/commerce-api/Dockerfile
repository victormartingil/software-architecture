FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -U -Dmaven.wagon.http.retryHandler.count=3 -DskipTests package
FROM eclipse-temurin:21.0.12.1_1-jre-jammy
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app app
WORKDIR /app
COPY --from=build /workspace/target/commerce-api-1.0.0.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /actuator/health/liveness HTTP/1.0\r\n\r\n" >&3; cat <&3 | grep -q UP'
ENTRYPOINT ["java","-jar","app.jar"]
