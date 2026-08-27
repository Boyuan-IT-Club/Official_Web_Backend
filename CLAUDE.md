# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概况

华东师范大学博远信息技术社的社团官网后端。Spring Boot 3.2.1 / Java 17 / MyBatis-Plus 3.5.9 / MySQL 8 / Redis / RabbitMQ / Flyway / Spring Security + JWT (jjwt 0.11.5)。核心业务是招新面试流程（学生投简历 → 填部门志愿与时间窗 → 管理员维护场次 → 算法自动分配面试），并深度集成飞书开放平台（多维表格同步、SMTP 通知）。

## 构建与测试

```bash
# 编译验证（首次下载依赖较慢）
./mvnw -q compile

# 打包（Dockerfile 从 target/ 取 jar）
./mvnw clean package -DskipTests

# 全量测试 —— 注意：@SpringBootTest 需要完整环境（MySQL+Redis+RabbitMQ，Flyway 从零迁移）
docker compose up -d mysql redis rabbitmq   # 先起依赖
./mvnw -B clean test

# 单个测试类 / 单个方法
./mvnw test -Dtest=JwtTokenUtilTest
./mvnw test -Dtest=JwtTokenUtilTest#方法名
```

关键坑：

- **Maven 仓库**：项目用 `.mvn/settings.xml` + `.mvn/maven.config` 强制走 Maven Central，绕过全局 `~/.m2` 里可能配置的私服。不要删改这两个文件。IDEA 里需把 User settings file 指向 `.mvn/settings.xml`。
- **不要用 `| tail` 之类的管道判断 Maven 是否成功**——管道会掩盖真实退出码。
- 本地 `application.yml` 默认 MySQL 端口是 **3307**（compose 映射），不是 3306。
- **可复现构建**：pom 里 `project.build.outputTimestamp` 固定了归档时间戳，保证依赖不变时 Docker 依赖层逐字节一致（部署提速的基石），不要改成动态值。

## 架构

DDD 顶层语义分区，包根 `club.boyuan.official`：

- `domain/` —— 业务域，按功能聚合 controller + service + dto：`user/`（含 auth/role/permission/department）、`resume/`、`interview/`（核心域，含 `scheduling/` 排期分配算法）、`activity/`、`system/`（全局搜索）
- `persistence/` —— entity + MyBatis-Plus mapper 接口（XML 在 `resources/mapper/`，namespace 用全限定类名）
- `integration/feishu/` —— 飞书开放平台集成
- `messaging/` —— RabbitMQ 配置 + **事务发件箱 outbox**（写库与发消息同事务，由 leader 节点定时投递）
- `infra/` —— config / filter(JWT) / ratelimit / sse / seckill / scheduler / notification
- `common/` —— exception（`BusinessException` + `BusinessExceptionEnum` + 全局处理器）、utils、MapStruct converter、跨域 DTO（`ResponseMessage`、`PageResultDTO`）

要点：

- **数据库 schema 唯一来源是 Flyway**（`resources/db/migration/V1..V7`），`ddl-auto=none`，已移除 JPA——新表/改表一律新增迁移脚本，不要改历史版本。
- **面试预约走"方案B"**（志愿+时间窗+算法分配）；旧"方案A秒杀"代码（`infra/seckill`）仍在但已关闭（`booking.seckill.enabled=false`），别当成现行逻辑。
- 鉴权：`SecurityConfig` 中 permitAll 的公开路径为 `/api/auth/**`、`/api/health*`、`/api/public/**`、`/uploads/**`、actuator 健康端点，其余接口需要 `Authorization: Bearer {JWT}`。
- DTO 映射用 MapStruct（编译期生成），不用反射 BeanUtils。

## 接口文档

`openapi.yaml`（仓库根目录）是 API 文档的唯一真实来源，由 Apifox 管理格式。**改接口必须同步改它**；合入 main 后 `.github/workflows/apifox-sync.yml` 会自动导入 Apifox（按路径+方法覆盖匹配接口）。注意文档是 OpenAPI 3.0.1：可空类型写 `nullable: true`，不能写 `type: 'null'`；鉴权统一走全局 `bearerAuth` security scheme，公开接口显式 `security: []`，不要给接口手写 `Authorization` header 参数。

## CI/CD 与部署

- `ci.yml`：PR 与非 main 推送 → 挂 MySQL/Redis/RabbitMQ 服务容器跑全量测试 + JaCoCo 覆盖率 + Docker 构建冒烟测试（只构建不推送）
- `docker-push.yml`：main 推送（纯文档改动除外）→ 测试门禁 → 构建推送 `boyuanclub/official-core-api`（自动递增 SemVer tag）→ 双机**并行**预拉镜像 → 先 B 后 A 滚动重启 + 健康检查
- `ai-review.yml`：PR 时调 DeepSeek 做代码审查回帖，失败不阻断
- Dockerfile 是 Spring Boot layertools **分层镜像**（依赖层与应用层分离），入口是 `org.springframework.boot.loader.launch.JarLauncher`——改 Dockerfile 时保持分层结构，否则部署拉取会退化为全量

生产是双机集群（2026-08 已从阿里云迁至腾讯云）：Node A（公网 124.221.222.206 / 内网 10.0.0.12，nginx 入口 + 主数据层 + outbox/定时任务 leader）、Node B（公网 124.221.230.58 / 内网 10.0.0.13，应用 + 数据热备，`OUTBOX_ENABLED=false`）。部署目录 `~/boyuan-official/`，两台从 Docker Hub 拉镜像。服务器登录凭据只放 gitignore 的配置清单，严禁写进任何入库文件。运维 Runbook 在团队飞书 wiki（源文件若存在于本地 `docs/feishu-wiki/`，该目录已 gitignore 不入库）。

## 安全红线

- `docs/feishu-wiki/v2/00-快速开始/00-9-配置清单-需填写.md` 已 gitignore，含真实服务器密码与密钥，**严禁提交或复制其内容到任何会提交的文件**。
- `.deploy/` 目录（部署私钥）同样已 gitignore，禁止提交。

## Agent skills

### Issue tracker

Issues 在本仓库的 GitHub Issues(前端的票也跟踪在此)。见 `docs/agents/issue-tracker.md`。

### Triage labels

默认五标签:needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix。见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文:`CONTEXT.md`(领域词汇表)+ `docs/adr/` 在仓库根。见 `docs/agents/domain.md`。
