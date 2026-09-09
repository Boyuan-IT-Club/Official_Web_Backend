# ── 阶段 1：解包 Spring Boot 分层 fat-jar ─────────────────────────
# 按 layertools 把 jar 拆成 dependencies / spring-boot-loader /
# snapshot-dependencies / application 四层。依赖不变时前三层内容逐字节一致
# （配合 pom 的 project.build.outputTimestamp 可复现构建），
# 服务器每次部署只需拉取几百 KB 的 application 层，而非整个 fat-jar。
FROM eclipse-temurin:17-jre-jammy AS extract
WORKDIR /extract
COPY target/Official-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── 阶段 2：运行镜像 ──────────────────────────────────────────────
# 注：openjdk 官方镜像已废弃下架，改用仍维护的 Eclipse Temurin。
# 用 JRE（而非 JDK）基础镜像：运行期无需编译器/JDK 工具，缩小镜像体积。
FROM eclipse-temurin:17-jre-jammy

# 仅安装 PDF 中文渲染所需的最小字体集：
#   - fontconfig / libfontconfig1：字体配置库
#   - fonts-noto-cjk：思源黑体，覆盖常用中日韩汉字（简历导出中文所需）
#   - fonts-dejavu-core：覆盖拉丁字符
# 刻意不装 fonts-noto-cjk-extra（数百 MB，仅含罕用扩展字，简历用不到）、
# fonts-noto、dejavu-extra 等大体积包，以大幅缩小镜像。
# Acquire::Retries:ubuntu 源偶发 502 Bad Gateway,加重试避免构建随机失败
RUN apt-get update -o Acquire::Retries=5 && \
    apt-get install -y --no-install-recommends -o Acquire::Retries=5 \
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

WORKDIR /official

# 分层复制：变动频率低的层在前，仅最后的 application 层随代码变化
COPY --from=extract /extract/dependencies/ ./
COPY --from=extract /extract/spring-boot-loader/ ./
COPY --from=extract /extract/snapshot-dependencies/ ./
COPY --from=extract /extract/application/ ./

# 暴露端口
EXPOSE 8080

# 添加JVM参数以支持UTF-8编码和字体处理
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djava.awt.headless=true -Djava.awt.fontconfig=/etc/fonts/fonts.conf -Dsun.java2d.fontpath=/usr/share/fonts"

# 启动应用（Spring Boot 3.2+ 的 loader 入口在 loader.launch 包下）
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher", "--spring.profiles.active=prod"]
