# 飞书表格同步说明

## 当前能力

当前飞书同步支持两个方向：

1. 平台 -> 飞书：把平台中的面试安排和简历字段导出到飞书多维表格。
2. 飞书 -> 平台：从飞书多维表格拉回面试结果、录取部门，并可更新用户部门。

两类任务都是异步执行：

```text
HTTP 创建任务 -> Redis 保存任务状态 -> MQ 投递 taskId -> 消费者执行 -> Redis 更新结果 -> 轮询/SSE 返回进度
```

## 接口

### 平台导出到飞书

```http
POST /api/interview/feishu/import
Authorization: Bearer <token>

{
  "cycleId": 2,
  "slotId": 55,
  "forceUpdate": false,
  "feishuTableUrl": "可选，覆盖 slot 上配置的表格 URL"
}
```

权限：

```text
resume:audit
```

返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "taskId": 42,
    "status": "PENDING"
  }
}
```

### 从飞书拉回平台

```http
POST /api/interview/feishu/import-from-table
Authorization: Bearer <token>

{
  "cycleId": 2,
  "feishuTableUrl": "https://xxx.feishu.cn/base/xxx?table=tblxxx",
  "updateUserDept": true
}
```

### 查询任务状态

```http
GET /api/interview/feishu/import/tasks/{taskId}
```

### SSE 订阅任务状态

```http
GET /api/interview/feishu/import/tasks/{taskId}/stream
Accept: text/event-stream
```

事件名：`status`

终态：

- `SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILED`

## 平台 -> 飞书执行方案

执行器：`FeishuImportExecutor`

流程：

1. 查询 `interview_schedule`：
   - `cycle_id = request.cycleId`
   - `status = 1`
   - 默认 `sync_status = 0`
   - 如果传 `slotId`，只导出该 slot
   - 如果 `forceUpdate=true`，包含已同步记录
2. 预加载：
   - `interview_slot`
   - `resume`
   - 简历字段快照
3. 按面试地点分桶：
   - `locationKey = interview_slot.location`
   - `tableUrl = request.feishuTableUrl` 或 `interview_slot.feishu_table_url`
4. 每个地点桶并行写飞书。
5. 写入前自动补齐飞书字段。
6. 有 `feishu_record_id` 的记录走更新；没有的走创建。
7. 创建/更新成功后回写：
   - `sync_status = 1`
   - `feishu_record_id`

注意：这里的“按地点分桶”依赖 `interview_slot.location`。如果不同地点对应不同飞书表，应在 slot 上配置不同 `feishu_table_url`。

## 飞书字段

列名定义在 `FeishuBitableColumns`，必须和飞书表格字段名一致：

| 字段 | 用途 |
| --- | --- |
| `姓名` | 学生姓名 |
| `意向部门` | 简历中第一/第二志愿等 |
| `年级` | 简历字段 |
| `专业` | 简历字段 |
| `自我介绍` | 简历字段 |
| `简历评分` | 平台简历评分 |
| `第一类问题` | 面试官填写 |
| `第二类问题` | 面试官填写 |
| `第三类问题` | 面试官填写 |
| `面试评价` | 面试官填写 |
| `预选` | 面试官填写 |
| `是否调剂` | 面试官填写 |
| `记录人` | 面试官填写 |
| `录取部门` | 拉回平台时使用 |
| `部门` | 兼容旧列名 |
| `面试是否通过` | 拉回平台时使用 |
| `面试是否通过（预选）` | 拉回平台时使用 |
| `决定人` | 拉回平台时使用 |

字段自动创建由 `FeishuBitableFieldSyncService.ensurePushExportFields` 完成。

## 飞书 -> 平台执行方案

执行器：`FeishuTablePullImportExecutor`

流程：

1. 校验招募周期存在。
2. 调飞书 API 读取全表记录。
3. 预加载：
   - 部门表
   - 当前周期所有简历
   - 当前周期学生用户
   - 当前周期有效面试安排
   - 已有面试结果
   - 决定人用户
4. 逐行处理飞书记录：
   - 姓名为空：跳过。
   - 按姓名在当前周期匹配用户。
   - 根据 `录取部门` 匹配系统部门。
   - 根据复选框解析面试结果 decision。
   - 创建或更新 `interview_result`。
   - 如果 `updateUserDept=true`，同步更新 `user.dept_id`。
5. 逐行返回成功/失败原因。

## 任务状态存储

Redis key：

```text
official:feishu:sync:task:{taskId}
official:feishu:sync:lock:{taskId}
official:feishu:sync:task:seq
```

任务字段包括：

- `taskId`
- `taskType`
- `status`
- `importedCount`
- `failedCount`
- `skippedCount`
- `totalSteps`
- `completedSteps`
- `progressPercent`
- `result`
- `pullResult`
- `errorMessage`

TTL 由配置控制：

```yaml
feishu:
  task-ttl-days: 7
  task-lock-ttl-hours: 24
```

## 配置项

```yaml
feishu:
  app-id: ${FEISHU_APP_ID:}
  app-secret: ${FEISHU_APP_SECRET:}
  api-base-url: ${FEISHU_API_BASE_URL:https://open.feishu.cn}
  batch-size: ${FEISHU_BATCH_SIZE:100}
  parallel-bucket-concurrency: ${FEISHU_PARALLEL_BUCKET_CONCURRENCY:3}
  list-page-size: ${FEISHU_LIST_PAGE_SIZE:500}
  api-max-retries: ${FEISHU_API_MAX_RETRIES:3}
  api-initial-backoff-ms: ${FEISHU_API_INITIAL_BACKOFF_MS:500}
  api-max-backoff-ms: ${FEISHU_API_MAX_BACKOFF_MS:8000}
  circuit-failure-threshold: ${FEISHU_CIRCUIT_FAILURE_THRESHOLD:5}
  circuit-open-seconds: ${FEISHU_CIRCUIT_OPEN_SECONDS:30}
```

生产环境必须通过环境变量注入 `FEISHU_APP_ID` 和 `FEISHU_APP_SECRET`，不能把真实值提交到仓库。

## 使用前准备

1. 飞书开放平台创建应用。
2. 配置多维表格权限。
3. 设置环境变量：

```bash
export FEISHU_APP_ID=...
export FEISHU_APP_SECRET=...
```

4. 为面试时段配置 `feishu_table_url`，或在导出请求中传 `feishuTableUrl`。
5. 确认飞书表格字段名和 `FeishuBitableColumns` 一致。

## 可完善点

- 拉回时按姓名匹配存在重名风险，建议增加学号/邮箱作为唯一匹配列。
- 当前平台 -> 飞书不直接导出 `interview_time/location` 字段，后续可补充面试时间、地点列。
- 对飞书字段自动创建增加字段类型迁移能力，例如字段已存在但类型不一致时提示修复。
- 对拉回任务增加“仅预览不写库”模式。
- 支持按地点/slot 单独拉回，而不是只能整表拉回。
- 飞书 API 错误码应结构化记录，便于定位权限、限流、字段类型错误。
