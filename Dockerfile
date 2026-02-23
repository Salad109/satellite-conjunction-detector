# Build stage
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw clean package -DskipTests && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx8g", "-XX:+UseShenandoahGC", "org.springframework.boot.loader.launch.JarLauncher"]
