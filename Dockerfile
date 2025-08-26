FROM openjdk:17-jdk-slim

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    locales \
    fonts-noto-cjk \
    && locale-gen zh_CN.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

# 设置中文环境变量
ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8

# 设置工作目录
WORKDIR /official

# 复制应用JAR包
COPY Official-*.jar official.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "official.jar", "--spring.profiles.active=prod"]