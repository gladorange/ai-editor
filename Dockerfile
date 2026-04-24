# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

RUN --mount=type=cache,id=cacheKey-maven-deps,target=/root/.m2 ./mvnw -B dependency:go-offline

COPY src/ src/

RUN --mount=type=cache,id=cacheKey-maven-build,target=/root/.m2  ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
