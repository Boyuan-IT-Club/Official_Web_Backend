# ADR-0001: 评测报告经派生仓库 GitHub Actions 推送公开端点入库(零认证,荣誉系统)

## Context

候选人评测在本地完成,产物是加密报告单。官网收数有四种候选路径:手动上传文件、工具加 `submit` 命令直传、Actions 带 token 推送、Actions 走公开端点推送。我们选择最后一种:**候选人的派生仓 `grade.yml` 在解密展示后,把加密报告单 POST 到官网公开端点**,零认证。

选它的理由:
1. **零配置采纳率**:候选人 push 即提交,不需要注册官网→生成 token→塞 repo secrets 三步;9-13 招新面对陌生人池,摩擦是命门。
2. **身份可靠**:POST 携带的 `github_username` 取自 GitHub context(`github.actor`),默认流程下候选人无法伪造;而报告单里的 `author` 是 `git config user.name` 自由文本,不可靠。
3. **安全体位不恶化**:加密密钥本就公开(荣誉系统),候选人又是自己 workflow 的 owner,token 挡不住任何真实攻击,只挡得住"忘了配 token"的诚实候选人。
4. 对比:手动上传保留为兜底可能性但本轮不做;`submit` 命令需改工具仓 + 官网发 token,二期候选。

防护不靠认证,靠:**rate limit(Redis 滑动窗口,现成)+ 报告内容 sha256 幂等去重 + 未认领提交(unclaimed)由管理员后台关联**。

## Status

accepted

## Considered Options

- **手动上传**(官网登录后传文件):兜底保留,本轮不做——登录流程 + 手动操作摩擦大。
- **npx autograding submit**:需要工具仓加命令 + 官网签发个人 token + 候选人配置;二期候选。
- **Actions + 每候选人 token**:不增加真实安全(候选人拥有 workflow 和公开密钥),纯增摩擦。
- **Actions 公开端点(本决策)**:见上。

## Consequences

- 公开端点须 permitAll + 限频;非法载荷 400,超频 429。
- 身份可信度是"默认可信、蓄意可伪造",与现状(密钥公开)一致;防作弊是 Phase 2(沙箱/服务器重刷)话题,不归本轮。
- "push 即提交"语义:候选人的每次 push 都自动产生一条官网提交;模板仓 README 必须写明,避免候选人无意"交卷"。
- 模板仓 `grade.yml` 需新增 POST 步骤(失败不阻断,Job Summary 自反馈优先)——已以 issue 形式同步给工具仓团队。
- 去重键 = sha256(加密报告单字节);workflow 重跑/重复 push 不产生重复行。