# syntax=docker/dockerfile:1

# ============================================================
# WorkFlowX Backend — multi-stage build（Phase 16; P16-03/04）
# Build stage: JDK 21 + Maven（Aliyun 镜像加速依赖下载）
# Runtime: JRE 21 + 非 root 用户 + curl（healthcheck）
# ============================================================

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Maven 阿里云镜像（与开发机 ADR 策略一致，加速依赖下载）
COPY backend/maven-settings.xml /root/.m2/settings.xml

# 先拷贝 pom 下载依赖（利用层缓存；源码变更不触发重新下载）
COPY backend/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline

# 再拷贝源码构建（跳过测试——功能回归由四线测试体系独立承担）
COPY backend/src ./src
RUN mvn -B -q package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# curl 供 healthcheck 使用；创建非 root 用户
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --shell /usr/sbin/nologin workflowx \
    && chown -R workflowx:workflowx /app

COPY --from=build /build/target/workflowx-backend-*.jar /app/app.jar

USER workflowx

# SIGTERM → Spring Boot 优雅关闭（默认 stop timeout 由 compose stop_grace_period 控制）
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
