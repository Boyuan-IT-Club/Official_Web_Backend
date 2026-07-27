# 运行阶段
# 注：openjdk 官方镜像已废弃下架，改用仍维护的 Eclipse Temurin。
# 用 JRE（而非 JDK）基础镜像：应用是可执行 fat-jar，运行期无需编译器/JDK 工具，
# 可显著缩小镜像体积、加快从节点(Node B)拉取，避免部署拉取超时。
FROM eclipse-temurin:17-jre-jammy

# 仅安装 PDF 中文渲染所需的最小字体集：
#   - fontconfig / libfontconfig1：字体配置库
#   - fonts-noto-cjk：思源黑体，覆盖常用中日韩汉字（简历导出中文所需）
#   - fonts-dejavu-core：覆盖拉丁字符
# 刻意不装 fonts-noto-cjk-extra（数百 MB，仅含罕用扩展字，简历用不到）、
# fonts-noto、dejavu-extra 等大体积包，以大幅缩小镜像。
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        fontconfig \
        libfontconfig1 \
        fonts-noto-cjk \
        fonts-dejavu-core \
        && rm -rf /var/lib/apt/lists/* \
        && fc-cache -f

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
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djava.awt.headless=true -Djava.awt.fontconfig=/etc/fonts/fonts.conf -Dsun.java2d.fontpath=/usr/share/fonts"

# 启动应用
ENTRYPOINT ["java", "-jar", "official.jar", "--spring.profiles.active=prod"]
