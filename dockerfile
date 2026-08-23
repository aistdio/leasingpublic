# syntax=docker/dockerfile:1
### build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -f pom.xml -ntp dependency:copy-dependencies

COPY src ./src
RUN mvn -B -f pom.xml -ntp package -DskipTests

### runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar ./app.jar

RUN groupadd -r app && useradd -r -g app app \
 && chown -R app:app /app

USER app

EXPOSE 8443 8000

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENV JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000"

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS $JAVA_DEBUG_OPTS -jar /app/app.jar"]
