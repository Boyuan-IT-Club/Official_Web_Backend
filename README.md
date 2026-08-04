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
- Spring Boot 3.2.1
- MyBatis-Plus 3.5.9 + MySQL 8.0+（数据访问统一走 MyBatis-Plus，未使用 JPA/Hibernate 持久化）
- Spring Security + JWT (jjwt 0.11.5)
- Redis（缓存与限流）
- RabbitMQ（Spring AMQP，配合事务发件箱 Outbox 异步投递）
- Flyway（数据库版本化迁移，作为唯一 schema 来源；`ddl-auto=none`）
- MapStruct 1.5.5（编译期 DTO/实体映射，替换反射式 BeanUtils）
- Java 17

### 代码结构
采用顶层语义分区（方案 C）：

```
club.boyuan.official
├─ domain/        业务域，按功能聚合 controller + service + service.impl + dto
│   ├─ user/      （含 auth/role/permission/department 及其 dto）
│   ├─ resume/    ├─ interview/（含 scheduling 排期算法与 dto）
│   ├─ activity/  └─ system/（含 global-search dto）
├─ persistence/   持久化内核：entity + mapper 接口（对应 resources/mapper/*.xml）
├─ integration/   外部系统集成：feishu 飞书（含其 dto）
├─ messaging/     消息：RabbitMQ 配置 + outbox 事务发件箱
├─ infra/         基础设施：config / filter / ratelimit / sse / seckill / scheduler / notification
├─ common/        通用能力：exception / utils / converter(MapStruct) 及跨域 dto（ResponseMessage、PageResultDTO）
└─ OfficialApplication
```

DTO 按所属功能就近归入 `domain.<feature>.dto` 与 `integration.feishu.dto`，仅 `ResponseMessage`、
`PageResultDTO` 等跨域通用响应保留在 `common.dto`。其中 `@MapperScan` 指向 `persistence.mapper`，
`type-aliases-package` 指向 `persistence.entity`，Mapper XML 的 `namespace` 与 `resultType/parameterType`
均使用对应的全限定类名。

### Maven 仓库说明
本项目通过 `.mvn/settings.xml` + `.mvn/maven.config` 强制使用 Maven 中央仓库，
不依赖任何私服，且不会影响全局 `~/.m2/settings.xml`。
若在 IntelliJ IDEA 构建，请在 `Settings → Build Tools → Maven` 中将
"User settings file" 覆盖指向本项目的 `.mvn/settings.xml`。

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

#### 使用 Docker Compose 启动依赖 (推荐)

```bash
# 启动依赖服务 (MySQL、Redis、RabbitMQ)
docker compose up -d mysql redis rabbitmq

# 查看服务状态
docker compose ps

# 停止服务
docker compose down
```

然后在IDE中运行 `src/main/java/club/boyuan/official/OfficialApplication.java` 文件中的 main 方法启动应用。

#### 传统方式
```bash
# 构建项目
./mvnw clean package

# 运行项目
./mvnw spring-boot:run
```

或者直接运行 `src/main/java/club/boyuan/official/OfficialApplication.java` 文件中的 main 方法。

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

#### 版本号跳转解决方案

为了避免版本号跳转带来的困惑，建议采取以下措施：

1. **保持清晰的版本发布策略**：在项目初期（0.x.x阶段）使用自动版本管理，当功能稳定后手动升级到1.0.0
2. **规范Git标签使用**：仅在重要里程碑使用手动标签，如重大功能发布或重大更新
3. **文档记录**：在CHANGELOG.md中记录每个版本的重要变更，便于追踪版本变化
4. **沟通协调**：团队成员间保持良好沟通，了解何时该使用自动版本，何时该使用手动标签

### 本地测试环境部署

```bash
# 构建 jar 包（Dockerfile 从 target/ 复制）
./mvnw clean package -DskipTests

# 构建并启动完整测试环境（包括应用、MySQL、Redis、RabbitMQ）
docker compose up -d --build

# 查看服务状态
docker compose ps

# 停止测试环境
docker compose down
```

### 生产环境部署（CI/CD 自动完成）

生产部署由 GitHub Actions 全自动完成，无需手动操作：

1. 代码合并到 `main` 分支后触发 `.github/workflows/docker-push.yml`
2. 测试门禁 → 构建并推送镜像 `boyuanclub/official-core-api` 到 Docker Hub
3. 双机（Node A/B）并行预拉镜像 → 先 B 后 A 滚动重启 → 健康检查与负载均衡验证

详见 `.github/workflows/docker-push.yml` 与飞书 wiki《09-1 CICD 与发布流程》。

### 手动 Docker 部署（应急备用）

1. 构建项目:
   ```bash
   # Linux/macOS
   ./mvnw clean package -DskipTests
   
   # Windows
   .\mvnw.cmd clean package -DskipTests
   ```

2. 构建 Docker 镜像:
   ```bash
   docker build -t boyuanclub/official-core-api:latest .
   ```

3. 推送到 Docker Hub:
   ```bash
   docker push boyuanclub/official-core-api:latest
   ```

4. 在服务器上运行:
   ```bash
   cd ~/boyuan-official
   docker compose pull official
   docker compose up -d official
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