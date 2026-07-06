# Step 1: Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src src

RUN mvn clean package -DskipTests -B

# Step 2: Runtime Stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/urlShortner-*.jar app.jar

EXPOSE 10020

ENTRYPOINT ["sh", "-c", "java -Xmx256m -jar app.jar --server.port=${PORT:-10020}"]