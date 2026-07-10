FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_OPTS=""

# 固定运行 UID/GID，避免应用进程和上传文件继续由 root 持有。
# curl 仅供容器内健康检查使用，安装后立即清理 apt 索引以控制镜像体积。
RUN groupadd --system --gid 10001 meetingos \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin meetingos \
    && apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/uploads \
    && chown -R meetingos:meetingos /app

COPY --from=build --chown=10001:10001 /app/target/meetingos-backend-*.jar /app/meetingos-backend.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/meetingos-backend.jar"]
