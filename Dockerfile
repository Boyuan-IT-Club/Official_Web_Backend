FROM openjdk:17-jdk-slim

# 安装必要的工具和字体库
RUN apt-get update && apt-get install -y \
    locales \
    fontconfig \
    libfreetype6 \
    fonts-noto-cjk \
    curl \
    jq \
    && locale-gen zh_CN.UTF-8 \
    && update-locale LANG=zh_CN.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

# 设置中文环境变量
ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8

# 创建目录
WORKDIR /official

# 复制应用文件
COPY target/official-*.jar official.jar

# 暴露端口
EXPOSE 8080

# 添加JVM参数以支持UTF-8编码和字体处理
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djava.awt.headless=true"

# 启动应用
ENTRYPOINT ["java", "-jar", "official.jar", "--spring.profiles.active=prod"]