# ADR-0003: 权限码按职责重划,拆开超载的 resume:audit

## Context

现有 RBAC 有 13 个权限码、5 个角色。以下数字来自当前 main 的迁移脚本与全仓 `@PreAuthorize` 统计:

| 权限码 | 保护的接口数 |
| --- | --- |
| `resume:audit` | 29 |
| `role:assign` | 16 |
| `admin:manage` | 10 |
| `cycle:manage` | 6 |
| `evaluation:view` | 4 |
| `resume:view` / `interview:evaluate` / `dept:manage` / `activity:manage` | 3 / 3 / 3 / 3 |
| `user:view` | 1 |
| `member:manage` / `award:manage` / `permission:manage` | **0 / 0 / 0** |

另有 60 个接口是 `isAuthenticated()`,不看角色。

五个具体问题:

1. **`resume:audit` 是伪装成权限的超级用户位。** 29 处引用分布在 9 个控制器,覆盖简历字段定义、
   简历状态与删除、飞书地点映射与推拉、评价表开启与锁定、评分维度配置、录取结果、改期审批、
   一键分配、场次与预约总览。没法只让某人"批简历"而不让他改录取结果 —— 共用同一个码。
2. **管理员碰不到用户。** 用户相关写操作全挂 `admin:manage`,而该码只给超管(V6 种子)。
   ADMIN 角色连录取为社员、分配部门、冻结账号都做不了,全部积压到超管。
3. **三个死权限。** `member:manage` / `award:manage` / `permission:manage` 全后端 0 引用。
   管理员持有前两个,看着能管社员和奖项,实际管不了 —— 权限矩阵在说谎。
4. **管理端准入是"有任意一个权限码"**(`permissionCodes.length > 0`)。社员持 `resume:view`、
   面试官持 `interview:evaluate`,都能登进后台再撞 403。
5. **社员能读全站简历。** `resume:view` 解锁"查任意用户某周期简历""简历列表""全局搜索",
   而它是社员角色的唯一权限。

## Status

proposed

## Decision

权限码从 13 个调整为 18 个,按"能独立授予的一件事"切分。

`resume:audit` 的 29 处按职责拆成六份:

| 新权限码 | 职责 | 处数 |
| --- | --- | --- |
| `resume:audit`(收窄) | 改简历状态、删除简历 | 2 |
| `cycle:manage`(并入) | 简历字段定义 —— 字段本就按周期定义 | 4 |
| `interview:schedule` | 场次、时间窗、一键分配与导出、改期审批、预约总览 | 7 |
| `interview:result` | 录取结果读写、群发通知邮件 | 2 |
| `interview:board:manage` | 评价表开启与锁定、维度配置、加权总分 | 7 |
| `feishu:sync` | 飞书地点映射、推送面试安排、拉回录取表格 | 7 |

`admin:manage` 的 10 处拆成 `user:manage`(录取/部门/冻结/编辑/删除)、
`admin:grant`(授予撤销管理员)、`system:ops`(清缓存)。

`role:assign` 的 16 处拆成 `role:assign`(给用户分配角色,5 处)与
`permission:manage`(角色定义 3 + 角色-权限绑定 5 + 权限本身 3,共 11 处)——
后者由此从死权限变成真实权限。

新增 `console:access` 作为管理端准入的显式门票,取代"有任意权限码"的判定。

`member:manage` 与 `award:manage` 废弃(0 引用,语义分别被 `user:manage` 覆盖 / 走用户自助接口)。

角色矩阵:超管持全部 18 项;管理员持除 `admin:grant`、`role:assign`、`permission:manage`、
`system:ops` 外的 14 项(招新业务全部下放);面试官持 `console:access`、`interview:evaluate`、
`evaluation:view`;社员与申请人不持任何后台权限。

超管与管理员的差别由此收敛为"决定谁有权限"和"动系统"两类。

### 面试官为什么不给 resume:view

面试官要看的是自己场次的候选人简历,而那条接口
(`/api/interview/evaluation/cycles/{cycleId}/candidates/{scheduleId}/resume`)本身就接受
`interview:evaluate`,并在代码里按场次限定范围。给 `resume:view` 等于放开全站简历。

### 两处注解之外的硬编码必须同步改

- `collab-server/src/config.js`: `REQUIRED_PERMISSIONS = ['interview:evaluate','resume:audit']`
  与 `ADMIN_PERMISSION = 'resume:audit'` —— 写评价的真实路径走协同服务,不在注解覆盖范围内。
  不改会让管理员因 `resume:audit` 语义收窄而失去评价表管理级身份。
- `InterviewEvaluationController` 第 48 行 `private static final String ADMIN_AUTHORITY = "resume:audit"`
  —— 不是注解,按 `@PreAuthorize` 搜索会漏掉。

## Considered Options

- **只删三个死权限,不动 `resume:audit`**:成本最低,但一号问题(无法细分招新职责)照旧,
  且死权限被删后矩阵仍然表达不了"看简历但不定录取"。
- **给 ADMIN 直接加 `admin:manage`**:两行 SQL 解决二号问题,但等于把"授予管理员"
  也一并给出去 —— 管理员可以自己造管理员,权限边界失效。
- **按角色写死判断(去掉 RBAC)**:角色少时更直观,但每加一种职责就要改代码,
  与已有的 `role_permission` 表和 JWT 声明冲突。
- **按职责重划权限码(本决策)**:一次性成本约 50 处注解 + 1 个 Flyway 迁移,
  换来后续新角色靠配置拼装、不改代码。

## Migration

三步均可独立部署,任一步后系统可用,存量账号全程不掉权限。

1. **加新码、双授权、不删旧的。** 迁移脚本插入新码并绑定角色;注解改为
   `hasAnyAuthority('interview:schedule','resume:audit')` 形式。此时任何账号的可达接口集合不变。
   沿用"按 `permission_code` 判存在、不写死 `permission_id`"的写法 —— 写死 ID 正是
   V10/V13 撞号(`evaluation:view` 被 `INSERT IGNORE` 静默跳过)的原因,见 V18。
2. **切准入与协同服务,收回旧码。** 前端准入改判 `console:access`;同步改 collab-server
   两个常量与后端那个 `ADMIN_AUTHORITY` 常量;从 `role_permission` 移除旧码绑定。
   社员将不再能登入管理后台 —— 本次唯一的行为回退,需提前告知。已登录用户须重新登录,
   权限码写在 JWT 里。
3. **清理。** 注解去掉兼容用旧码;删除废弃权限的 `permission` 行与残留绑定。
   可等一个招新周期结束后再做。

## Open Questions

需要业务侧决定,非技术选择:

1. 社员是否保留读全站简历的能力。本 ADR 按"移除"设计;若需老社员帮看简历,
   更合适的是给那几个人加面试官角色,而不是给全体社员开权限。
2. "录取为社员"是否同时授予社员角色。目前批量录取只写 `user.is_member`,不动 `user_role`,
   两者完全独立。建议界面上明确区分"社员身份"与"后台角色"两个动作。
3. 管理员是否可以定录取结果与群发通知。本 ADR 给了 `interview:result`;
   若希望只由超管做,把这一格改为不授予即可 —— 这正是拆分带来的余地。
4. 是否新增"招新组"角色(`console:access` + `resume:view` + `resume:audit` +
   `interview:schedule`),给招新期临时帮忙的同学,结束即撤。不需改代码。
