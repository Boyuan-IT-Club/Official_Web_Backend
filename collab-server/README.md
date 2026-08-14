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
├─ meta:    Y.Map   { cycleId, locked, seededAt }
├─ columns: Y.Array<Y.Map>   评分维度列 + 评语 + 推荐意见 + 公共备注
└─ rows:    Y.Map<scheduleId, Y.Map>
     ├─ _info                候选人只读快照（服务端播种与刷新）
     ├─ 'notes'              全员共享的公共备注（Y.Text）
     ├─ 'dim:12:7'           面试官 7 在维度 12 上的评分
     ├─ 'comment:7'          面试官 7 的评语（Y.Text，支持字符级合并）
     ├─ 'recommendation:7'   推荐意见 1倾向通过 / 2待定 / 3不倾向
     └─ 'status:7'           1草稿 / 2已提交
```

评语格在播种时就预建好 Y.Text，而不是等首次输入时再建——两个客户端同时在空格子里创建 Y.Text
会各建一个，合并时只留一个，另一人刚敲的字会直接消失。

## 权限的诚实边界

CRDT 的本质是全副本可写，**协议层无法阻止一个已连接的客户端写任意单元格**。因此权限靠三层兜底：

1. **UI 层**：scoped 列只把 `<colId>:<自己的userId>` 渲染为可编辑；
2. **物化层**：本服务如实上报每个单元格的实际写入者（`originUserId`，服务端记录，客户端无法伪造），
   Java 校验它必须等于单元格归属的面试官，不符则丢弃；
3. **审计层**：每次物化按用户写一条 `collab_audit`，越权的标 `rejected=1`。

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

## 部署要点

- 容器 `official-collab` 跑 **Node A**，内存约 80–150MB
- nginx 增加 `/collab/` location，必须带 `proxy_http_version 1.1` 与 Upgrade 头，并调大 WS 读超时
- **必须固定单实例**：本服务在内存里持有 Y.Doc，若把 `/collab` 负载均衡到两个节点，
  两边各持一份互不同步的文档副本，面试官会看不见彼此的编辑。要多实例需先接 Hocuspocus 的 Redis 扩展。

## 配置

见 `.env.example`。`JWT_SECRET` 必须与 Java 后端完全一致（HS256，原始 UTF-8 字节），
`COLLAB_SERVICE_TOKEN` 必须与后端的同名配置一致，否则内部接口调用会被 401。
