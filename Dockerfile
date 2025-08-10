# 使用官方OpenJDK 17作为基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 复制jar文件到容器中
COPY target/Official-0.0.1-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# 运行应用
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]