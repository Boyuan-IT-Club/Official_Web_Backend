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

1. **`resume:audit` 是伪装成权限的超级用户位。** 29 处引用分布在 10 个控制器,覆盖简历字段定义、
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

accepted · 阶段一(#163)与阶段二(#164 + 前端 #71)已落地,阶段三待做

## Decision

权限码从 13 个调整为 18 个,按"能独立授予的一件事"切分。

`resume:audit` 的 29 处按职责拆成六份:

下表的「注解数」是 `@PreAuthorize` 的出现次数,不等于接口数 —— 类级注解会覆盖该控制器的
全部接口。例如 `interview:result` 只有 1 处类级注解,但它管着 5 个接口。
(本文初稿把这两个口径混着写了,以下为实测更正值。)

| 新权限码 | 职责 | 注解数 |
| --- | --- | --- |
| `resume:audit`(收窄) | 改简历状态、删除简历 | 2 |
| `cycle:manage`(并入) | 简历字段定义 —— 字段本就按周期定义 | 4 |
| `interview:schedule` | 场次、时间窗、一键分配与导出、改期审批、预约总览 | 8 |
| `interview:result` | 录取结果读写、群发通知邮件 | 1（类级,覆盖 5 个接口） |
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
`system:ops`、`dept:manage` 外的 13 项(招新业务全部下放,部门管理见 Resolved 第 4 条);
面试官持 `console:access`、`interview:evaluate`、`evaluation:view`;
社员与申请人不持任何后台权限。

超管与管理员的差别由此收敛为"决定谁有权限"、"动系统"和"改组织架构"三类。

### 面试官为什么不给 resume:view

面试官要看的是自己场次的候选人简历,而那条接口
(`/api/interview/evaluation/cycles/{cycleId}/candidates/{scheduleId}/resume`)本身就接受
`interview:evaluate`,并在代码里按场次限定范围。给 `resume:view` 等于放开全站简历。

### 三处注解之外的硬编码必须同步改

- `collab-server/src/config.js`: `REQUIRED_PERMISSIONS = ['interview:evaluate','resume:audit']`
  与 `ADMIN_PERMISSION = 'resume:audit'` —— 写评价的真实路径走协同服务,不在注解覆盖范围内。
  不改会让管理员因 `resume:audit` 语义收窄而失去评价表管理级身份。
- `InterviewEvaluationController` 的 `ADMIN_AUTHORITY = "resume:audit"` 常量
  —— 不是注解,按 `@PreAuthorize` 搜索会漏掉。只改注解不改它,管理员会在接口层放行、
  却在方法内部被当成普通面试官(它决定能否跨场次查看候选人简历)。
- `collab-server/src/diag.js` 的提示文案原本叫用户去要 `resume:audit`,拆分后该改为
  `interview:board:manage` —— 不改不会出错,但会把人指向一个错的权限。

另有一处初稿没提到的遗漏:`InterviewScheduleController.autoAssignInterviewsByCycleId`
也用 `resume:audit`,且写成 `hasAuthority(('resume:audit'))` —— 双层括号,
SpEL 里能跑(`('x')` 只是带括号的字符串字面量),按常规形式搜索容易漏掉。

## Considered Options

- **只删三个死权限,不动 `resume:audit`**:成本最低,但一号问题(无法细分招新职责)照旧,
  且死权限被删后矩阵仍然表达不了"看简历但不定录取"。
- **给 ADMIN 直接加 `admin:manage`**:两行 SQL 解决二号问题,但等于把"授予管理员"
  也一并给出去 —— 管理员可以自己造管理员,权限边界失效。
- **按角色写死判断(去掉 RBAC)**:角色少时更直观,但每加一种职责就要改代码,
  与已有的 `role_permission` 表和 JWT 声明冲突。
- **按职责重划权限码(本决策)**:实测一次性成本 47 处注解 + 2 个 Flyway 迁移(V23/V24)
  + 3 处注解之外的常量,换来后续新角色靠配置拼装、不改代码。

## Migration

三步均可独立部署,任一步后系统可用,存量账号全程不掉权限。

### 阶段一 —— 已落地(V23,#163)

加新码、双授权、不删旧的。注解改为 `hasAnyAuthority('interview:schedule', 'resume:audit')`
形式,共 47 处。此阶段部署后任何账号的可达接口集合都没有变化。

映射不是按行号硬编码,而是解析每处注解下方最近的方法名后重写 —— 行号会随编辑漂移,
方法名不会。

迁移脚本沿用"按 `permission_code` 判存在、不写死 `permission_id`"的写法 ——
写死 ID 正是 V10/V13 撞号(`evaluation:view` 被 `INSERT IGNORE` 静默跳过)的原因,见 V18。

同期新增 `PermissionSeedConsistencyTest`:断言每个被 `@PreAuthorize` 引用的权限码都能在
迁移脚本里找到。守的是那类静默失效 —— 注解要求某个码而它从未进过 `permission` 表时,
Spring 只对所有人 403,不会有任何提示。纯文本比对,不需要 Spring 上下文与数据库。

### 阶段二 —— 已落地(V24 + 前端 #71,#164)

切准入与协同服务,收回废弃码。

- 前端准入从"权限码数量 > 0"改判 `console:access`
- `InterviewEvaluationController` 的 `ADMIN_AUTHORITY` 改为集合
  `{interview:board:manage, resume:audit}`
- `collab-server` 的 `REQUIRED_PERMISSIONS` 增加 `interview:board:manage`;
  `ADMIN_PERMISSION` 改为 `ADMIN_PERMISSIONS` 集合;diag 提示文案跟上
- V24 收回 `admin:manage`(已拆)、`member:manage`、`award:manage`(两者 0 引用)的授权。
  刻意不动 `resume:audit` 与 `role:assign` —— 它们是收窄保留,仍是新模型里的正式权限

**为什么没有出现中途失权**:JWT 的 `permissionCodes` 是登录时烙进令牌的,V24 只影响
此后新签发的令牌;而注解仍同时接受新旧码,所以在线用户在令牌过期前照常工作。

**刻意留下的三处过渡兼容**(等旧令牌全部过期后即可拆除):

| 位置 | 兼容内容 |
| --- | --- |
| 前端 `jwt.ts` | `LEGACY_CONSOLE_PERMISSIONS` 列表 —— 只认 `console:access` 会把当时在线的管理员挡在门外,且提示是"该账号没有管理权限",完全误导。列表是明确枚举的管理类权限,`resume:view` 刻意不在其中(那正是社员曾能进后台的原因) |
| 后端 `BOARD_ADMIN_AUTHORITIES` | 并存 `resume:audit` |
| `collab-server` 两个常量 | 并存 `resume:audit` |

### 阶段三 —— 待做

注解去掉兼容用的旧码;删除 `admin:manage` / `member:manage` / `award:manage` 的
`permission` 行与残留绑定;拆掉上表那三处过渡兼容。

此步纯清理,且会撤掉过渡期的安全垫,建议等一个招募周期结束、旧令牌确定全部过期后再做。

## Resolved

原 Open Questions 已由业务侧决定,并已先行落地(V22 + 相关代码,见 #162):

1. **社员不保留读全站简历。** `resume:view` 已从社员角色移除。
   连带效果:社员角色至此不持有任何权限码,而管理端准入是"权限码数量 > 0",
   因此社员不再能登入管理后台 —— 期望行为。需要老社员帮看简历时,给那几个人加面试官角色。
2. **"录取为社员"同时授予社员角色。** `syncMemberRole` 在批量与单个两条路径上
   一并同步 `user_role`,开除社员时移除绑定。按 `role_code` 查 MEMBER,不写死 ID。
3. **管理员可以定录取结果与群发通知。** 本就成立 —— `InterviewResultController` 是类级
   `resume:audit`,管理员持有该权限,无需改动。本 ADR 的 `interview:result` 仍授予管理员。
4. **部门增删改收归超管**(本 ADR 之后新增的决定)。`dept:manage` 已从管理员角色移除,
   上文角色矩阵中管理员的该格随之改为不授予;超管与管理员的差别由四项变为五项。
5. 是否新增"招新组"角色仍未决,不阻塞其余部分。

## Addendum: 面试官的评价表按周期隔离(已修复)

同期修复的一个越权问题,记录在此因为它属于同一套权限模型:

`collab-server` 的 `onAuthenticate` 只校验令牌里有没有 `interview:evaluate`,
不校验"这个人是不是这个周期的面试官"。协同文档名形如 `eval-board:{cycleId}`,
于是 A 周期的面试官把周期号一改就能打开 B 周期的评价表,看到 B 周期全部候选人的
名单与分数。候选人简历接口本身已按场次限定(`isInterviewerOf`),漏的是评价表这一层。

修法:新增内部接口 `GET /api/internal/evaluation/cycles/{cycleId}/interviewers/{userId}`,
判定依据是 `session_interviewer` join `interview_session` 后该用户在本周期是否绑定过场次;
`onAuthenticate` 对非管理员调用它,不通过即拒,管理员跨周期是职责所需直接放行。
该检查刻意放在 `loadDoc` 之前 —— 否则会先抛出"该周期评价表尚未开启",
等于向无关人员泄露别的周期开没开表。新增 diag 码 `NOT_CYCLE_INTERVIEWER`,
避免又一个无差别的 `permission-denied`。

由 `CycleInterviewerScopeIntegrationTest` 锁住:绑定 A 周期场次的面试官对 B 周期必须为 false。

## Addendum: 面试当日提醒已下线

`InterviewReminderScheduler` 只保留前一日 12:00 那一轮。当天早上再发一封,
对已收到前一日提醒的人是重复打扰,真忘了的人也来不及改安排。
`InterviewNotificationType.DAY_REMINDER` 与邮件模板保留未删,
`dispatchReminders` 仍接受该类型,将来要恢复只需加回一个 `@Scheduled` 方法。
