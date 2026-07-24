# CI/CD 与发布流程

> 社团官网后端 · 持续集成与发布。**已落地**四条 GitHub Actions 工作流：PR 质量门禁、主干构建部署、AI 代码审查、AI 生成测试。

## 一、工作流总览

| 工作流 | 文件 | 触发 | 作用 |
|---|---|---|---|
| **CI** | `.github/workflows/ci.yml` | PR、非 main 分支 push | 编译 + 单元测试 + JaCoCo 覆盖率（质量门禁） |
| **构建部署** | `.github/workflows/docker-push.yml` | push `main`、`v*.*.*` 标签、手动 | 测试 → 构建推镜像 → 双机滚动部署 → LB 校验 |
| **AI 代码审查** | `.github/workflows/ai-review.yml` | PR opened/synchronize | DeepSeek 审查 diff 并回帖到 PR |
| **AI 生成测试** | `.github/workflows/ai-test-gen.yml` | 手动（输入类名） | AI 产出 JUnit5 测试草稿并开 Draft PR |

```
   开发分支 ──PR──▶ [CI: test+coverage] + [AI Review]  ──合并──▶ develop ──PR──▶ main
                                                                              │ push main
                                                          [test]→[build&push]→[deploy B→A]→[LB校验]
```

## 二、CI（PR 质量门禁）

`ci.yml`：`pull_request` 与非 `main` 分支 push 触发。
1. Checkout + JDK 17（temurin，maven 缓存）
2. `./mvnw -B clean test`（**不 skip 测试**，JaCoCo 随 `test` 阶段产出报告）
3. 解析 `target/site/jacoco/jacoco.csv`，把**指令/分支覆盖率**写入流水线 Summary
4. 上传 `surefire-reports` 与 `jacoco-coverage` 作为 Artifact

> 覆盖率当前偏低，暂不设硬阈值（只观测）；随《10-2 AI 测试方案》逐步补测后再考虑加 `jacoco:check` 门槛。

## 三、构建与部署（main）

`docker-push.yml`：push `main` / `v*` 标签 / 手动触发。
1. **test 任务**：`./mvnw -B clean test`（测试不过不发布）
2. **build 任务**：`./mvnw clean package -DskipTests`（测试已在上一任务跑过）→ Buildx 构建 → 登录 Docker Hub（`secrets.DOCKERHUB_TOKEN`）→ 版本号计算（标签用标签值，否则查 Docker Hub 最新版本 +1 补丁）→ 推送 `boyuanclub/official-core-api:latest` 与 `:{版本}`
3. **deploy 任务**（双机滚动，先 B 后 A，降低同时中断）：
   - SSH 到 Node B → `docker compose pull official && up -d official` → 等 `/actuator/health` UP
   - SSH 到 Node A → 同上
   - 经 Node A 的 nginx 校验 `/api/user/login`（401/400 即反代通）

> 部署账号 `official-boyuan-club`，项目目录 `~/boyuan-official/`；集群拓扑见《09-5 双机集群实际部署与故障切换 Runbook》。

## 四、AI 代码审查

`ai-review.yml` + `.github/scripts/ai_review.sh`：PR 触发，把 diff（仅 `src/pom.xml/.github/compose/Dockerfile`）发给 **DeepSeek**，按「🔴必须修复/🟡建议/🟢小问题」回帖到 PR。
- 仅本仓库分支 PR 生效（Fork PR 拿不到密钥自动跳过，安全）。
- 未配置 `DEEPSEEK_API_KEY` 时自动跳过，不阻断。
- 定位为**辅助**，人工 review 仍是必须。

## 五、AI 生成测试

`ai-test-gen.yml` + `.github/scripts/ai_test_gen.sh`：**手动触发**，输入被测类名（如 `SessionAssignmentServiceImpl`）与目标分支。
1. 定位源码 → 解析包名 → 目标测试路径 `src/test/java/.../<类>Test.java`（已存在则跳过）
2. 读源码 + 一个现有测试作风格参考 → DeepSeek 生成 JUnit5+Mockito 测试
3. 写文件 → 新分支 → 开 **Draft PR**
> AI 产物是**草稿**，必须人工审核（删幻觉断言、补边界、确保 `./mvnw test` 通过）后再合并。

## 六、所需 GitHub Secrets

> Settings → Secrets and variables → Actions。**绝不入库**。可用 `gh secret set <NAME>` 配置。

| Secret | 用途 | 说明 |
|---|---|---|
| `DOCKERHUB_TOKEN` | 推送镜像 | Docker Hub 账号 `boyuanclub` 的 Access Token |
| `DEPLOY_HOST_A` | 部署 | `8.159.153.140` |
| `DEPLOY_HOST_B` | 部署 | `8.159.150.156` |
| `DEPLOY_USER` | 部署账号 | `official-boyuan-club` |
| `DEPLOY_SSH_KEY` | 部署私钥 | 部署密钥私钥内容（本地 `.deploy/id_deploy`） |
| `DEEPSEEK_API_KEY` | AI 审查/生成测试 | DeepSeek 开放平台密钥（缺失则 AI 步骤跳过） |

> `GITHUB_TOKEN` 由 Actions 自动注入（用于回帖 PR / 建 PR），无需手动配置。

## 七、分支与发布策略

- 功能分支 → PR 到 `develop`（触发 CI + AI 审查，需通过）
- `develop` → PR 到 `main`（仅管理员可合并）
- push `main` 自动构建、推镜像、双机滚动部署
- 正式版可打 `v*.*.*` 标签发布指定版本

## 八、发布 Checklist

1. 功能在本地/测试验证通过；PR 的 CI（测试+覆盖率）与 AI 审查通过
2. 合并到 `develop` → 再 PR 合并到 `main`（或打 `v*` 标签）
3. 关注 Actions：test → build&push → deploy(B→A) → LB 校验
4. 验证：两台 `/actuator/health` UP，`http://8.159.153.140/api/...` 经 LB 正常
5. 异常回滚：`docker compose` 指定上一个 `:{版本}` 镜像重启（见 09-5）

---
*目标：合并即测试、推送即部署、AI 放大质量、密钥不入库。*
