# IoT 云平台镜像（deploy 模式：PostgreSQL 持久化，前端静态资源已内嵌 jar；数据库由 docker-compose 提供）
# 代码改动后需先在 iot-backend 目录用 Maven 重新打包，再 docker compose build：
#   C:\Users\wmstea\tools\apache-maven-3.9.16\bin\mvn.cmd clean package -DskipTests
FROM eclipse-temurin:17-jre-alpine

ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai"

WORKDIR /app
COPY iot-backend/target/iot-platform-2.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=deploy"]
