# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home app && mkdir -p /app/data && chown app /app/data
COPY --from=build /app/target/*.jar app.jar
USER app
# Free hosts give ~512 MB RAM: keep the JVM well inside it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k"
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
