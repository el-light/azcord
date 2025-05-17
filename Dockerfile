# 1. BUILD STAGE
FROM maven:3.9.4-eclipse-temurin-17 AS build

# where to copy & build
WORKDIR /app

# copy just pom.xml first to leverage Docker cache
COPY pom.xml .
# if you have a settings.xml, copy it here too:
# COPY .mvn/ .mvn/
# COPY mvnw .

# download dependencies
RUN mvn dependency:go-offline -B

# now copy the rest of your code
COPY src ./src

# package without running tests
RUN mvn clean package -DskipTests -B

# 2. RUNTIME STAGE
FROM eclipse-temurin:17-jre-jammy

# optional: configure a non‐root user for better security
# RUN useradd -m spring
# USER spring

# expose your app port
EXPOSE 8082

# copy the fat JAR from the builder
ARG JAR_FILE=target/azcord-0.0.1-SNAPSHOT.jar
COPY --from=build /app/${JAR_FILE} /app/app.jar

# you can drop in any ENV vars your app needs here, e.g.:
# ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/azcord
# ENV SPRING_DATASOURCE_USERNAME=root
# ENV SPRING_DATASOURCE_PASSWORD=secret

ENTRYPOINT ["java","-jar","/app/app.jar"]
