# 构建阶段
FROM openjdk:17-jdk-slim AS builder

# 安装Maven
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 下载并安装Maven（使用更稳定的源）
RUN cd /tmp && \
    curl -O https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz && \
    tar -xzf apache-maven-3.9.9-bin.tar.gz && \
    mv apache-maven-3.9.9 /opt/maven

ENV MAVEN_HOME=/opt/maven
ENV PATH=$MAVEN_HOME/bin:$PATH

# 创建工作目录
WORKDIR /official

# 复制Maven配置和源代码
COPY pom.xml .
COPY src ./src

# 构建项目
RUN mvn clean package -DskipTests

# 运行阶段
FROM openjdk:17-jdk-slim

# 设置中文环境变量（基础支持，不依赖 locale-gen）
ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8

# 创建目录
WORKDIR /official

# 从构建阶段复制JAR文件
COPY --from=builder /official/target/Official-*.jar official.jar

# 暴露端口
EXPOSE 8080

# 添加JVM参数以支持UTF-8编码和无头模式
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djava.awt.headless=true"

# 启动应用
ENTRYPOINT ["java", "-jar", "official.jar", "--spring.profiles.active=prod"]