# Official Project

这是一个基于 Spring Boot 的后端服务系统，使用 Maven 构建，支持 Docker 部署。

## 项目简介

Official 是一个社团管理系统，旨在为社团提供完整的成员管理、简历投递和面试安排等功能。系统支持用户注册登录、权限管理、简历提交、面试调度等核心功能。

主要功能包括：
- 用户管理：注册、登录、修改信息、重置密码
- 认证与权限：基于 Spring Security 的 JWT 认证机制
- 简历投递：支持学生提交个人简历和获奖经历
- 面试安排：自动或手动分配面试时间
- 奖项经验管理：管理员可管理用户的奖项经验信息
- 全局搜索：支持用户和奖项信息的全局搜索和高级筛选

## 技术架构

### 后端技术栈
- Spring Boot 3.5.3
- MyBatis 3.5.15 + MySQL 8.0+
- Spring Security + JWT (jjwt 0.11.5)
- Redis
- Java 17

### 版本管理
- 使用语义化版本号（SemVer）规范：主版本号.次版本号.补丁版本号
- 默认采用自动版本管理机制：向main分支推送代码时，CI/CD流程会自动递增补丁版本号
- 支持通过Git标签手动指定版本号
- Docker镜像版本与项目版本号保持一致

### 版本号跳转说明

你可能会注意到版本号有时会跳转，例如从 `0.0.1` 直接跳到 `1.0.0`。这通常是由以下原因之一造成的：

1. **手动标签推送**：当使用Git标签（如 `git tag v1.0.0`）触发构建时，系统会直接使用标签中的版本号
2. **里程碑版本发布**：当项目达到一个重要里程碑时，开发者可能会手动将版本从 `0.x.x` 提升到 `1.x.x`
3. **首次构建**：如果仓库中没有任何语义化版本标签，系统会从 `0.0.1` 开始

### 开发工具
- JDK 17
- Maven 3.x
- IDE（如 IntelliJ IDEA）

## 安装与运行

### 开发环境运行

#### 使用Makefile (推荐)

```bash
# 启动依赖服务 (MySQL和Redis)
make dev-up

# 查看服务状态
make status

# 停止服务
make dev-down
```

然后在IDE中运行 [OfficialApplication.java](file:///C:/Users/35183/IdeaProjects/Official/src/main/java/club/boyuan/official/OfficialApplication.java) 文件中的 main 方法启动应用。

#### 传统方式
```bash
# 构建项目
./mvnw clean package

# 运行项目
./mvnw spring-boot:run
```

或者直接运行 [OfficialApplication.java](file:///C:/Users/35183/IdeaProjects/Official/src/main/java/club/boyuan/official/OfficialApplication.java) 文件中的 main 方法。

## API 接口文档

本项目使用 Apifox 管理 API 接口文档，不再通过依赖方式生成文档。

## 部署说明

### Docker镜像版本管理

本项目采用语义化版本号（SemVer）管理机制：

#### 自动版本管理
当向main分支推送代码时，CI/CD流程会自动执行以下操作：
1. 从DockerHub获取当前最新版本号
2. 自动递增补丁版本号（例如：1.0.0 → 1.0.1）
3. 使用新版本号构建和推送Docker镜像

#### 手动指定版本号
您也可以通过创建Git标签来手动指定版本号：
```bash
git tag v1.2.3
git push origin v1.2.3
```
这样CI/CD流程会使用标签中的版本号（1.2.3）而不是自动生成版本号。

#### 验证版本号更新
1. 查看GitHub Actions日志
   - 进入仓库的Actions选项卡
   - 选择"Push to Docker Hub on main branch merge"工作流
   - 查看"Build and push Docker image"步骤的输出

2. 检查DockerHub标签
   - 访问DockerHub仓库页面
   - 查看Tags选项卡中的版本列表

3. 使用本地测试脚本验证版本管理
   ```bash
   # 测试版本自增逻辑
   bash test_version_bump.sh
   
   # 测试从DockerHub获取真实标签
   bash test_dockerhub_version.sh
   ```
   
#### 版本号跳转解决方案

为了避免版本号跳转带来的困惑，建议采取以下措施：

1. **保持清晰的版本发布策略**：在项目初期（0.x.x阶段）使用自动版本管理，当功能稳定后手动升级到1.0.0
2. **规范Git标签使用**：仅在重要里程碑使用手动标签，如重大功能发布或重大更新
3. **文档记录**：在CHANGELOG.md中记录每个版本的重要变更，便于追踪版本变化
4. **沟通协调**：团队成员间保持良好沟通，了解何时该使用自动版本，何时该使用手动标签

### 本地测试环境部署

```bash
# 构建并启动完整测试环境（包括应用、MySQL和Redis）
make test-up

# 查看服务状态
make status

# 停止测试环境
make test-down
```

### Docker Hub 部署方式（生产环境推荐）

1. 登录到 Docker Hub：
   ```bash
   docker login -u boyuanclub
   ```

2. 构建并推送镜像到 Docker Hub：
   ```bash
   # 构建并推送镜像
   make deploy
   ```
   
   或者使用脚本方式：
   ```bash
   ./deploy.sh
   ```

3. 在服务器上更新应用：
   ```bash
   # 登录 Docker Hub（如果尚未登录）
   docker login -u boyuanclub
   
   # 拉取最新镜像并重启服务
   make update
   ```
   
   或者使用脚本方式：
   ```bash
   ./update.sh
   ```

### 手动 Docker 部署

1. 构建项目:
   ```bash
   # Linux/macOS
   ./mvnw clean package -DskipTests
   
   # Windows
   .\mvnw.cmd clean package -DskipTests
   ```

2. 构建 Docker 镜像:
   ```bash
   docker build -t boyuanclub/official:latest .
   ```

3. 推送到 Docker Hub:
   ```bash
   docker push boyuanclub/official:latest
   ```

4. 在服务器上运行:
   ```bash
   # 拉取最新镜像
   docker pull boyuanclub/official:latest
   
   # 启动服务
   docker-compose up -d
   ```

### 传统部署方式

1. 构建项目：
   ```bash
   ./mvnw clean package
   ```

2. 将生成的 JAR 文件上传到服务器：
   ```bash
   scp target/Official-0.0.1-SNAPSHOT.jar user@server:/path/to/app/
   ```

3. 在服务器上运行：
   ```bash
   java -jar Official-0.0.1-SNAPSHOT.jar
   ```

## 管理命令

项目提供了一系列便捷的管理命令：

```bash
# 显示所有可用命令
make help

# 构建项目
make build

# 本地启动应用
make local-up

# 查看服务日志
make dev-logs

# 初始化数据库
make init-db
```

## 第一阶段功能说明

### 简历投递功能
- 仅支持特定邮箱验证
- 简历包含个人简介、获奖情况等板块
- 支持自动分配面试时间或手动调度

### 面试安排
- 系统可根据规则自动分配面试时间
- 支持管理员手动调整面试时间
- 面试时间确认后通知相关人员

## 注意事项

1. 部署前请确保已正确配置环境变量
2. 数据库连接需要正确配置用户名和密码
3. 邮件服务需要配置 SMTP 相关参数
4. 短信服务需要配置相应的访问密钥