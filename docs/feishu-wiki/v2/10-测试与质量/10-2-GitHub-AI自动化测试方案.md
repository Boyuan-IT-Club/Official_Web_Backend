# GitHub AI 自动化测试方案

> 社团官网后端 · 测试与质量。**P0（测试进 CI + JaCoCo）、P1（PR 级 AI 代码审查）、P2 工具（AI 生成测试草稿）已落地**；P2 补测与 P3 冒烟为持续迭代项。落地细节见《09-1 CI/CD 与发布流程》。

## 1. 测试现状

- 框架：JUnit 5 + Spring Boot Test + Spring Security Test（见 `pom.xml`）。
- 现有测试（`src/test`，共 14 个）已覆盖部分核心：
  - 面试分配算法：`InterviewSlotTimeCalculatorTest`、`InterviewUrgencyScorerTest`、`ClassroomAssignerTest`、`SessionAssignmentServiceImplTest`、`InterviewFineSlotTimeServiceTest`
  - 库存/秒杀：`InterviewSlotInventoryServiceImplTest`、`SeckillLuaResultTest`、`InterviewBookingRedisKeysTest`
  - 基础设施：`JwtAuthenticationFilterTest`、`JwtTokenUtilTest`、`MessageOutboxRelayTest`、`FeishuTableUrlParserTest`
  - Web：`UserRoleControllerTest`、`OfficialApplicationTests`
- **缺口**：Service 层整体覆盖偏低；多数 Controller、飞书导入执行器、Outbox 端到端、限流、通知链路缺测试。
- **CI 现状（已改进）**：`ci.yml` 在 PR/非 main 分支 push 时跑 `./mvnw -B clean test` + JaCoCo 覆盖率；`docker-push.yml` 的 `build` 前置独立 `test` 任务，测试不过不发布。

## 2. 「AI 自动化测试」目标

用 AI 在 GitHub 流程里做三件事：
1. **AI 代码审查**：每个 PR 自动做一轮 AI review（找 bug、坏味道、安全问题）。
2. **AI 辅助生成测试**：对覆盖率低的模块，用 AI 生成单元/集成测试草稿，人工审核后合入。
3. **AI 冒烟/回归辅助**：对关键接口做基于自然语言用例的检查（可选，进阶）。

## 3. 落地方案

### 3.1 前置：先把常规测试接入 CI（基础）
新增 `.github/workflows/ci.yml`（`pull_request` 触发）：
```yaml
name: CI
on: pull_request
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Run tests
        run: ./mvnw -B verify
      - name: Coverage report (JaCoCo)
        run: ./mvnw jacoco:report   # 需在 pom 引入 jacoco 插件
```
> 引入 JaCoCo 输出覆盖率，作为 AI 生成测试的靶点。

### 3.2 PR 级 AI 代码审查
在 `pull_request` 上跑 AI review（二选一或并存）：
- **Claude Code GitHub Action / Anthropic API**：把 PR diff 发给模型，产出结构化评审意见并回帖到 PR。密钥存 `secrets.ANTHROPIC_API_KEY`。
- **GitHub Copilot / 第三方 AI review bot**：按需接入。

要点：
- 只审查 **diff**，控制 token 成本。
- 输出：潜在 bug、空指针/边界、安全（鉴权/注入/密钥）、与本项目规范（统一异常、DTO 不泄漏实体等）的偏差。
- 定位为**辅助**，不作为唯一门禁，人仍需 review。

### 3.3 AI 辅助生成测试（提升覆盖率）
流程：
1. 按 JaCoCo 找覆盖率低、且**逻辑关键**的类（如 `InterviewAssignmentServiceImpl`、`InterviewScheduleServiceImpl`、飞书导入执行器、限流、通知）。
2. 用 AI 依据源码 + 现有测试风格生成测试草稿（给出被测类、依赖、边界场景）。
3. **人工审核**：删幻觉断言、补真实边界、确保可独立运行（Mock 外部依赖：DB/Redis/MQ/飞书）。
4. 合入后纳入 CI。

> 可做成半自动：一个手动触发的 workflow，输入类名 → AI 产出测试草稿 PR，人工把关。

### 3.4 AI 冒烟/回归（进阶，可选）
- 对 dev/staging 环境的关键接口（登录、简历投递、面试志愿提交、分配）用自然语言用例驱动，AI 生成/校验请求与断言。
- 谨慎用于写操作，优先只读与幂等接口。

## 4. 优先级与路线

| 阶段 | 事项 | 价值 |
|---|---|---|
| P0 | 测试接入 CI（`ci.yml` + `-DskipTests` 移除）+ JaCoCo | 让测试真正生效 |
| P1 | PR 级 AI 代码审查 | 低成本提升每次合并质量 |
| P2 | AI 辅助为核心 Service 补测试至合理覆盖率 | 抓住高风险逻辑 |
| P3 | AI 冒烟/回归 | 端到端兜底 |

## 5. 注意事项

- **API 密钥**：AI 相关密钥（`ANTHROPIC_API_KEY` 等）只存 GitHub Secrets，绝不入库。
- **成本控制**：只发 diff / 目标类，设并发与频率上限。
- **AI 产物必须人工把关**：生成的测试/评审是草稿，合入前需人 review，避免幻觉断言与误报。
- **不外泄敏感数据**：审查/生成时避免把真实密钥、用户数据发给外部模型。

## 6. 落地现状与后续

**已落地**（选型：**DeepSeek**，密钥 `secrets.DEEPSEEK_API_KEY`；成本低、OpenAI 兼容接口）：
- P0：`ci.yml` 跑测试 + JaCoCo 覆盖率（Summary 展示、Artifact 上传）。
- P1：`ai-review.yml` + `ai_review.sh`，PR 自动 AI 审查回帖。
- P2 工具：`ai-test-gen.yml` + `ai_test_gen.sh`，手动输入类名 → AI 生成测试草稿 → Draft PR。

**后续迭代**：
- 用 P2 工具按 JaCoCo 靶点为核心 Service（`SessionAssignmentServiceImpl`、`InterviewScheduleServiceImpl`、飞书导入执行器、限流、通知链路）补测，人工审核合入。
- 覆盖率达到基线后，在 `pom` 加 `jacoco:check` 阈值，纳入 CI 硬门禁。
- P3：对只读/幂等关键接口做 AI 冒烟（进阶，可选）。

---
*先让测试进 CI，再让 AI 放大质量。AI 是加速器，不是免检章。*
