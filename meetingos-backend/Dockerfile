# 默认基础镜像均固定为多架构 OCI index；发布流水线仍可通过 build args 显式升级。
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre@sha256:1824944ef1bd572d1ff0952afeb2fec7931d77c972c4fbc4dfcdf89f758fb490

FROM ${MAVEN_IMAGE} AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM ${RUNTIME_IMAGE}
USER root
WORKDIR /app
ENV JAVA_OPTS=""

# 固定运行 UID/GID，避免应用进程和上传文件继续由 root 持有。
# curl 仅供容器内健康检查使用，安装后立即清理 apt 索引以控制镜像体积。
RUN if ! getent group 10001 >/dev/null; then groupadd --system --gid 10001 meetingos; fi \
    && if ! getent passwd 10001 >/dev/null; then useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin meetingos; fi \
    && if ! command -v curl >/dev/null; then \
         apt-get update \
         && apt-get install --yes --no-install-recommends curl \
         && rm -rf /var/lib/apt/lists/*; \
       fi \
    && mkdir -p /app/uploads \
    && chown -R 10001:10001 /app

COPY --from=build --chown=10001:10001 /app/target/meetingos-backend-*.jar /app/meetingos-backend.jar
USER 10001:10001
EXPOSE 8080
# 保留 JAVA_OPTS 的参数展开，同时用 exec 让 JVM 接管 PID 1，确保 SIGTERM 能直接触发
# Spring Boot 优雅停机，不由中间 shell 吞掉或延迟容器停止信号。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/meetingos-backend.jar"]
