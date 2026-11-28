# 面试评价快照 HTTP 契约

四个接口使用统一的 `code/message/data` 信封。Node 以 `X-Service-Token` 提交专用的 `EVALUATION_SNAPSHOT_SERVICE_TOKEN`。浏览器发起的三个板操作还需传 `X-Evaluation-User-Token`，内容为当前连接中官网签发的原始 JWT。官网验证签名、过期、注销、账号 ID 和用户名，并重新核实账号启用状态与当前评价权限。此凭据仅供受信的 Node 进程持有，Node 先使用 `inspectUpdate` 解码 Yjs，在文档副本上验证更新，再由 `nextSnapshot` 生成完整署名快照。客户端提交的作者 ID 或字段列表不能作为服务端验证结果。

相对路径均位于 `/api/internal/evaluation` 下。

| 方法和路径 | 请求与成功结果 |
| --- | --- |
| GET `boards/{cycleId}/snapshot` | 返回 `docName,cycleId,state,locked,storedRevision,baseRevision,authors,scheduleIds,dimensionIds`。state 为 Base64 或 null。所有字段来自同一事务的一致快照。当前存储版本为零时，从本周期全部正式评价恢复署名及最高版本。获得评价权限的两人可读取同一周期的名单和维度。 |
| POST `boards/{cycleId}/commits` | 请求含 UUID `operationId`、非负安全整数 `expectedStoredRevision`、`nextRevision`、非空 Base64 `state`、完整 `authors` 和 Node 观察到的 `changedCells`，例如 `[["5001","comment"],["5001","dim:4:note"]]`。返回 `operationId,revision,digest`。digest 是解码后 state 的 SHA-256 小写十六进制。状态最大 8 MiB，UTF-8 JSON 总长最大 16 MiB，固定长度及分块传输均受限。 |
| GET `boards/{cycleId}/commits/{operationId}` | 调用时重新验证用户准入。只向原操作者返回同一文档的持久回执。回执随评价板保留。查无回执返回 404。 |
| GET `materialization/pending?cursor=&limit=20` | 仅需快照服务凭据。按文档名游标分页，limit 为 1 至 20。返回 `items:[{docName,cycleId,storedRevision,authors}],nextCursor`；下一页起点为上页末文档名，遍历完毕时为 null。 |

同一操作号的完整请求指纹包括基线和目标版本、Yjs 状态摘要、规范化署名和行列集合。响应丢失后先按板与操作号查询。即使已有后续提交，相同操作仍返回原始回执。原操作号中的内容被改变，或依据已经过时的存储版本提交另一份内容，返回 409；事务成功提交已拒绝快照的审计记录后，HTTP 层才响应。当前用户无权或板锁定返回 403，无效署名返回 400，超限返回 413，不可确认的数据库结果返回 503。

后台读取 pending 后，从每个条目的 `authors.rows` 提取 `editedRevision > 0` 的 `materialized`，连同 `docName、cycleId、snapshotRevision=storedRevision` 调用既有 `materialize`。该调用使用原面试协同凭据 `COLLAB_SERVICE_TOKEN`。现有服务会按已提交的板版本及行内容核对每次物化，表里还有欠缺的行，下轮从首页继续扫描。

## 发布与回切

Flyway 先由迁移账号创建 V80。官网运行身份须能读取板状态、正式评价、账号授权、候选名单和维度，更新 `collab_doc` 的 state、author_state、snapshot_revision，向 `collab_audit` 插入审计，向 `evaluation_commit_receipt` 插入和读取回执。SQL 写入者保留既有权限直到 R7a。

`EVALUATION_HTTP_WRITES_ENABLED` 默认为 false，写请求返回 503。影子读时只比较相同存储版本的两份结果，另外比对历史基线、署名、名单与维度。同版不等阻止切写。切换时停止接收连接、排空单写者并记录末次已提交版本，停用旧写入路径后再给选定的官网提供方启写、启动单活 Node HTTP 模式。回切时先排空 HTTP 写入者并关闭官网写开关，旧的单活 SQL 适配器加载最新已提交状态。一次请求超时不能据此启用另一个写入者。

## 测试夹具

`evaluation-snapshots.json` 的 Yjs 字节与署名由独立协同仓的 `baselineAuthors` 和 `nextSnapshot` 生成。固定同一候选行和两名合成面试官，覆盖旧存储版本 0、正式评价版本 7、两份竞争的版本 8 和后续编辑版本 9。`EvaluationSnapshotIntegrationTest` 用真实 MySQL 和签名用户令牌经过 HTTP 层验证回执、数据、冲突审计、锁板与撤权。
