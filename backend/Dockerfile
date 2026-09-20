# Dockerfile backend - CHI DUNG cho moi truong demo (TV6, tuan 8+).
# Luc code hang ngay thi chay bang IntelliJ hoac `mvn spring-boot:run`, KHONG can file nay.

# --- Giai doan 1: build ra file .jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Giai doan 2: chi chay file .jar (anh nho hon nhieu) ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
