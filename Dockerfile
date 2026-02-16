# Build stage
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends gcc libc6-dev make libgomp1 && rm -rf /var/lib/apt/lists/*
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN make -C src/main/c
RUN ./mvnw clean package -DskipTests && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# Runtime stage
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./
COPY --from=builder /app/src/main/c/pair_reduction.so /app/lib/pair_reduction.so

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx8g", "--enable-native-access=ALL-UNNAMED", "org.springframework.boot.loader.launch.JarLauncher"]
