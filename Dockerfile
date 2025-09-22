# 运行阶段
FROM openjdk:17-jdk-slim

# 安装字体和必要的依赖
RUN apt-get update && \
    apt-get install -y \
        fontconfig \
        fonts-dejavu \
        fonts-dejavu-core \
        fonts-dejavu-extra \
        fonts-noto \
        fonts-noto-cjk \
        fonts-noto-cjk-extra \
        fonts-liberation \
        libfontconfig1 \
        && rm -rf /var/lib/apt/lists/* \
        && fc-cache -fv

# 设置中文环境变量
ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8

# 创建目录
WORKDIR /official

# 复制应用JAR包
COPY target/Official-*.jar official.jar

# 暴露端口
EXPOSE 8080

# 添加JVM参数以支持UTF-8编码和字体处理
# 添加额外的字体配置和AWT设置
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djava.awt.headless=true -Djava.awt.fontconfig=/etc/fonts/fonts.conf -Dsun.java2d.fontpath=/usr/share/fonts"

# 启动应用
ENTRYPOINT ["java", "-jar", "official.jar", "--spring.profiles.active=prod"]