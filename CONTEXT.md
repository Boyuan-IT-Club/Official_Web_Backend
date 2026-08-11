# 招新评测域 (Autograder 集成)

官网(Autograder 集成的上下文,横跨后端与前端两个仓库):候选人本地评测 → GitHub Actions 推送报告单 → 官网入库展示 → 面试官人工评价。

## Language

**考核 (Examination)**
社团招新对候选人设置的自动评测关卡:四个 task(环境/git/Docker/Linux/Makefile),满分 400,候选人本地跑 `npx autograding grade` 完成。
_Avoid_: 考试、面试(面试是后续人工环节,见 interview_result)

**评测 (Evaluation)**
一次 `grade` 运行及其产物——一份加密报告单。
_Avoid_: 评分、测评(评分是得分的具体数字,测评偏产品话术)

**报告单 (Report, 文件 `autograding_report.json`)**
一次评测的加密产物,含 author、timestamp、四 task 得分与检查项明细、总分。AES-256-GCM 加密,**密钥公开(荣誉系统,防作弊不在本域职责)**。
_Avoid_: 成绩单

**提交 (Submission)**
官网收到并入库的一份报告单,归属一个官网用户与一个招新周期。身份键是 GitHub 登录名(github_username,来自 Actions 推送的 GitHub context);user_id 按用户绑定的 GitHub 账号匹配,**可空**——未匹配的提交处于"未认领"状态,由管理员关联。cycle_id 入库时按当前活跃周期自动归属,**可空**——无活跃周期时留空,管理员可手动归。同一候选人可有多个提交(每次 push 派生仓自动产生一份);招新决策默认取最新一份。
_Avoid_: 上传(上传特指用户手动操作,当前走 Actions 自动推送)

**未认领提交 (Unclaimed Submission)**
github_username 尚未匹配到任何官网用户账号的提交。管理员在后台将其关联到具体用户后转正。
_Avoid_: 幽灵提交

**GitHub 账号 (GitHub Account)**
官网用户资料里的绑定字段,关联候选人 GitHub 身份。注意:它由候选人在官网手动绑定,与报告单里的 author(`git config user.name` 自由文本)不是一回事;官方身份以 Actions 推送时 GitHub 提供的登录名为准。
_Avoid_: GitHub 昵称、author

**得分 (Score)**
一次评测的总分(0–400)或单 task 得分(各 0–100)。
_Avoid_: 分数(口语可混用)

**排行榜 (Leaderboard)**
管理员内置榜单:按招新周期/部门筛选,展示候选人姓名、部门、GitHub 账号、最高分、提交次数;行可点入该候选人的历史提交与得分详情。仅管理端可见(内部版),不公开。
_Avoid_: 公开榜、答题墙