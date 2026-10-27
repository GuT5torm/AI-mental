# ---- 构建阶段 ----
# 容器内使用镜像自带的 mvn，不用 mvnw（规避 Windows 检出 CRLF 导致脚本无法执行）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom 拉依赖，形成缓存层：pom 不变时重建直接命中
# （central 走阿里云镜像，见 docker/maven-settings.xml，加速国内网络构建）
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
# JVM 参数经 JAVA_OPTS 注入，如 -XX:MaxRAMPercentage=75.0
ENV JAVA_OPTS=""

# 非 root 运行；uploads 为文件上传落盘目录（生产编排中以具名卷挂载）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser:appuser /app
USER appuser

COPY --from=build /build/target/ai-springboot-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 1236

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:1236/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
