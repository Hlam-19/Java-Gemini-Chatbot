# ---------- Giai doan 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy pom truoc de Docker cache lop tai thu vien:
# chi khi pom.xml doi moi phai tai lai, sua code Java thi khong.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Giai doan 2: chay ----------
# Dung ban JRE nho gon, khong kem Maven va ma nguon
FROM eclipse-temurin:21-jre

WORKDIR /app

# Chay bang tai khoan thuong, khong phai root
RUN useradd -r -u 1001 appuser

COPY --from=build /build/target/chatbot-1.0-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080

# Bao cho Docker biet ung dung con song khong
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD curl -fsS http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
