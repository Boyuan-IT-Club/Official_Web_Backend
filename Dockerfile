FROM docker.xuanyuan.me/ubuntu:22.04

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    wget \
    tar \
    locales \
    fonts-noto-cjk \
    language-pack-zh-hans \
    && locale-gen zh_CN.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

# 设置中文环境变量
ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8

# 创建目录
RUN mkdir -p /official
WORKDIR /official

# 复制JDK安装包并解压
COPY jdk-17.0.12_linux-x64_bin.tar.gz /tmp/
RUN tar -xzf /tmp/jdk-17.0.12_linux-x64_bin.tar.gz -C /opt/ \
    && rm /tmp/jdk-17.0.12_linux-x64_bin.tar.gz

# 设置环境变量
ENV JAVA_HOME=/opt/jdk-17.0.12
ENV PATH=$PATH:$JAVA_HOME/bin

# 复制应用JAR包
COPY target/Official-0.0.1-SNAPSHOT.jar official.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/official/official.jar", "--spring.profiles.active=prod"]