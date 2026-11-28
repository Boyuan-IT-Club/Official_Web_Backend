# 三服务迁移 R0 契约基线

本目录记录官网 `7108ddf` 的 289 条显式 Controller 路由。相邻的工作台主线 `903ecbe` 有 32 条，合计 321 条。在 routes.tsv 中，一个方法的路径或显式动词别名各占一行，未限定动词记 ANY。21 条退役入口包括间接委托给 retiredWrite 的 O170。O171/O172 保持历史回执和 SSE。R0RouteBaselineTest 检查注册映射并调用退役处理方法；鉴权和代理行为仍需 HTTP 联调。

## 在隔离环境回放

| 样例 | 输入与断言 |
| --- | --- |
| 二进制简历 | 以有访问权的测试账号请求 GET /api/resumes/export/pdf/42，期望 application/pdf、Content-Disposition 的附件名 resume_42.pdf、与正文长度一致的 Content-Length 和以 %PDF- 开头的字节。不存在与未授权分别覆盖 404/403 |
| 旧预约回执 SSE | GET /api/interview/booking/requests/demo/stream，text/event-stream，首帧为 event:status，data 是含 requestId、status 的 JSON。SUCCESS/FAILED 后完成。以同一 requestId 通过普通 GET 再查结果 |
| 面试 WebSocket | 官网前端使用 HocuspocusProvider，路径 /collab，房间 eval-board:7003。种子、共享 Y.Doc 和署名字段见 Official_Web_Collab 的 test/frontend-contract.test.js。先存快照，再广播并物化 |
| SQL 与重启 | 存储版本 0、历史评价最高版本 7 的第一次编辑生成 8；相同目标版本且字节、署名都相同可确认；不同内容保留第一份和冲突审计。用 Official_Web_Collab 的 test/e2e/seed.sql 与 test/e2e/collab-e2e.mjs 在独立数据库核对同版重投、冲突、断开后补写 |
| 成员提供方 | R0 时官网尚缺七个操作。原始请求响应样例由 Workbench 主线的 workbench-start/src/test/resources/contracts/r0/service-contracts.json 维护。R1 须用真实 HTTP 对比，并核对 numeric id 与 string userId/membershipVersion |
| 工作台房间 | Workbench 已提供三个内部 POST 回调。工作台前端使用 /collab/workbench 自定义帧，样例随 Workbench 提交。R0 的面试 Node 尚未接入该房间 |

**OpenAPI 对账。** 官网规格有 254 个具体操作，239 个精确匹配，4 个由 ANY 承接。其余 11 项包括静态头像示例，以及排程删除、列表、批量导入、细分时段、手工调剂、通知状态、结果导出、面试统计、AI 搜索和 GitHub 绑定。以固定提交的映射为基线。候选接口进入后续 PR 时单独记录。

**验收记录口径。** 本批次的路由检查和快速测试、需要数据库的端到端回放、生产环境核实分别记录。部署交接需核对实际反向代理、镜像 digest、定时任务所有者及队列。R0 不重放生产通知。
