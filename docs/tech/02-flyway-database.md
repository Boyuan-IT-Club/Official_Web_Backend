# Flyway 数据库配置技术方案

## 目标

用 Flyway 管理数据库结构变更，避免多人开发和部署时依赖手动 SQL 或 Hibernate 自动改表。目标是：

1. 所有新增表、字段、索引通过 migration 文件记录。
2. 启动时自动校验并执行未运行的 migration。
3. 生产数据库可追踪每次结构变更。
4. 避免 `ddl-auto=update` 和手写 SQL 混用导致结构不可控。

## 当前现状

项目已引入 Flyway 依赖：

- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-mysql`

配置位置：

- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/application-loadtest.yml`

核心配置：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
    validate-on-migrate: true
    table: flyway_schema_history
```

已有 migration：

- `V1__baseline.sql`：基线版本，目前只执行 `SELECT 1`。
- `V2__message_outbox.sql`：创建 `message_outbox` 事务发件箱表。
- `V3__interview_schedule_feishu_record_id.sql`：为 `interview_schedule` 增加 `feishu_record_id` 和索引。
- `V4__interview_notification_log.sql`：创建面试通知日志表。
- `V5__repair_interview_schedule_and_notification_log.sql`：幂等修复 V3/V4 在旧库或空库下可能遗漏的结构。

## 当前风险

项目仍启用了：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

这意味着部分表结构可能由 Hibernate 自动维护，另一部分由 Flyway 维护。短期可兼容，但长期风险较高：

- 自动改表不会留下版本记录。
- migration 和实体变化可能不一致。
- 生产环境出现结构漂移后难排查。

## 推荐落地方式

### 阶段一：保持兼容，规范新增变更

短期不直接关闭 `ddl-auto=update`，但规定：

- 新增表必须写 Flyway migration。
- 新增字段、索引、唯一约束必须写 Flyway migration。
- 实体字段变更必须同步写 migration。
- migration 文件一旦合并，不允许修改旧文件，只能新增下一个版本。

命名规范：

```text
V{版本号}__{英文描述}.sql
```

示例：

```text
V6__add_resume_score.sql
V7__add_interview_slot_department_mapping.sql
```

### 阶段二：固化完整 schema

把当前稳定数据库结构整理为基线：

1. 从干净数据库导出完整 DDL。
2. 放入新的 baseline 脚本，或保持 `V1__baseline.sql` 作为历史基线并从 `V6` 开始严格增量。
3. 确认 `official.sql` 的角色：只作为本地初始化参考，不作为生产迁移入口。

### 阶段三：生产关闭 Hibernate 自动改表

生产环境建议改为：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

含义：

- Hibernate 只校验实体和表是否兼容。
- 真正建表/改表只能通过 Flyway。
- 避免生产启动时自动修改表结构。

## 新增 migration 的实施步骤

1. 确认当前最新版本号，例如 `V5`。
2. 新建 `src/main/resources/db/migration/V6__xxx.sql`。
3. SQL 尽量写幂等逻辑，尤其是修复类脚本：
   - `CREATE TABLE IF NOT EXISTS`
   - 通过 `information_schema` 判断列/索引是否存在
4. 本地启动或执行测试，确认 `flyway_schema_history` 出现新版本记录。
5. 不要修改已经跑过的 migration。

## 回滚策略

社区版 Flyway 不提供自动 undo。推荐：

- 小步提交 migration，降低单次变更风险。
- 对破坏性变更先做兼容字段，代码切换后再删旧字段。
- 生产执行前备份数据库。
- 如需回滚，新增修复 migration，而不是修改旧 migration。

## 完成清单

- 明确 `official.sql` 是开发初始化脚本，还是要迁移为 Flyway 基线。
- 新增 `.env.example`，把数据库连接、账号、密码全部环境变量化。
- 生产 profile 将 `ddl-auto` 改为 `validate`。
- 编写贡献规范：实体字段变更必须附带 Flyway migration。
- CI 中加入一次启动/迁移校验，至少在 MySQL service 下跑 `./mvnw test` 或启动上下文。
