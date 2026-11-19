# Stage 1: build the jar with Maven (no Java or Maven needed on the host).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# copy pom.xml first: dependencies are cached and only re-downloaded when the pom changes
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# tests need Docker themselves (Testcontainers), so they run outside the image build
RUN mvn -B package -DskipTests

# Stage 2: small runtime image with only the JRE and the jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
# run as a non-root user
RUN useradd --system --no-create-home spring
USER spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
