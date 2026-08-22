# 面试评价协同服务（official-collab）

面试评价表的实时协同后端：多位面试官同时编辑同一张表，毫秒级互见、断网续编、重连自动无冲突合并。
基于 **Yjs**（CRDT 引擎）+ **Hocuspocus**（Yjs 官方系服务端），Node 20，独立容器。

> 为什么不做进 Java：Java 没有成熟的 CRDT 实现。协同是面试现场的关键路径，
> 因此也刻意不与实验性的 AI 服务同容器，避免互相拖累。

## 它负责什么、不负责什么

| 负责 | 不负责 |
|---|---|
| WebSocket 连接鉴权（复用后端同一把 JWT） | 开表 / 锁表（管理端点按钮，Java 落 `collab_doc`） |
| 文档快照的加载与防抖落库（`collab_doc.state`） | 评分维度的增删（Java 管，本服务只读取播种） |
| 首次播种与名单对账（调 Java 内部接口拉数据） | 权限的最终判定（物化时由 Java 校验 origin） |
| 把编辑结果防抖物化回 `interview_evaluation` | 汇总统计（Java 读物化后的表） |

**协作模型**：同场次的几位面试官面同一个候选人，**一位候选人只有一份评价**，
任何一位绑定在该场次上的面试官都能补分数、接着写记录，并发写同一字段由 CRDT 收敛。
「谁参与过」由本服务在服务端旁路记录后上报，不编在单元格键名里（编在键名里客户端就能伪造成别人）。

## 数据流

```
浏览器 ──WS /collab──> 本服务 ──┬─ 快照 ──> MySQL collab_doc
                                 ├─ 播种 <── Java GET  /api/internal/evaluation/board/{cycleId}/seed
                                 └─ 物化 ──> Java POST /api/internal/evaluation/materialize
                                                          └─> interview_evaluation ─> 评价汇总 / 结果与通知
```

编辑期的真源是 Y.Doc，落库后业务真源是 MySQL。下游完全感知不到上游是 CRDT。

## 文档结构

```
Y.Doc
├─ meta:    Y.Map   { cycleId, locked, seededAt, interviewerNames }
├─ columns: Y.Array<Y.Map>   评分维度列 + 面试记录与评语 + 推荐意见
└─ rows:    Y.Map<scheduleId, Y.Map>
     ├─ _info             候选人只读快照 + 本场面试官绑定（服务端播种与刷新）
     ├─ 'dim:12'          维度 12 的评分
     ├─ 'comment'         面试记录与评语（Y.Text，支持字符级合并）
     ├─ 'recommendation'  共同结论 1倾向通过 / 2待定 / 3不倾向
     └─ 'status'          1进行中 / 2已定稿
```

评语格在播种时就预建好 Y.Text，而不是等首次输入时再建——两个客户端同时在空格子里创建 Y.Text
会各建一个，合并时只留一个，另一人刚敲的字会直接消失。

## 权限的诚实边界

CRDT 的本质是全副本可写，**协议层无法阻止一个已连接的客户端写任意单元格**。因此权限靠三层兜底：

1. **UI 层**：只有绑定在该场次上的面试官，那一行才渲染为可编辑；
2. **物化层**：本服务如实上报参与编辑的人（`contributors`，服务端旁路记录，客户端无法伪造），
   Java 逐个校验他们是否绑定在该行所属场次上，未绑定者被剔除出参与人；
3. **审计层**：每次物化按用户写一条 `collab_audit`，未授权的标 `rejected=1`。

**一个必须说清的取舍**：共编模型下分不出哪个字符是谁写的，因此无法只回滚未授权者的那一笔——
值仍会落库，只是他不被署名并留下审计。反过来整行拒绝的话，一个捣乱者就能让整场面试的记录都写不进去。

面试官是可信小群体，这个强度足够。**已知残留风险**：本服务不查 Java 的 Redis 令牌吊销名单，
已登出用户在令牌自然过期前仍可建立连接（见 `src/auth.js` 注释）。

## 本地运行

```bash
npm install
cp .env.example .env   # 填好 JWT_SECRET / COLLAB_SERVICE_TOKEN / 数据库连接
npm start
```

跑测试（纯逻辑，不需要数据库与网络）：

```bash
npm test
```

端到端联调（需要 MySQL + Java 后端 + 本服务都起着）：

```bash
# 1. 导入最小数据集：一个周期、一个场次、两位面试官、两位候选人
docker exec -i <mysql容器> mysql -uroot -p<密码> --default-character-set=utf8mb4 official \
  < test/e2e/seed.sql
# 2. 跑联调，覆盖共编收敛 → 物化 → 署名 → 越权审计 → 汇总
node test/e2e/collab-e2e.mjs
```

## 部署要点

- 容器 `official-collab` 跑 **Node A**，内存约 80–150MB
- nginx 增加 `/collab/` location，必须带 `proxy_http_version 1.1` 与 Upgrade 头，并调大 WS 读超时
- **必须固定单实例**：本服务在内存里持有 Y.Doc，若把 `/collab` 负载均衡到两个节点，
  两边各持一份互不同步的文档副本，面试官会看不见彼此的编辑。要多实例需先接 Hocuspocus 的 Redis 扩展。

## 配置

见 `.env.example`。`JWT_SECRET` 必须与 Java 后端完全一致（HS256，原始 UTF-8 字节），
`COLLAB_SERVICE_TOKEN` 必须与后端的同名配置一致，否则内部接口调用会被 401。
