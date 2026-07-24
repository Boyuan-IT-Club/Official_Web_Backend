#!/usr/bin/env bash
# AI 代码审查：把 PR 的 diff 发给 DeepSeek，产出评审意见并回帖到 PR。
# 依赖环境变量：
#   DEEPSEEK_API_KEY   —— DeepSeek API 密钥（GitHub Secret）
#   GITHUB_TOKEN       —— 由 Actions 自动注入，用于 gh 回帖
#   PR_NUMBER, BASE_SHA, HEAD_SHA —— 由 workflow 传入
# 可选：
#   REVIEW_MODEL       —— 默认 deepseek-chat；更强可用 deepseek-reasoner（R1，较慢较贵）
#   DEEPSEEK_BASE_URL  —— 默认 https://api.deepseek.com
#   MAX_DIFF_CHARS     —— diff 截断上限（默认 60000，控制 token 成本）
set -euo pipefail

MODEL="${REVIEW_MODEL:-deepseek-chat}"
BASE_URL="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}"
MAX_DIFF_CHARS="${MAX_DIFF_CHARS:-60000}"

# 只审查后端源码/配置/迁移/工作流相关改动，排除二进制与无关文件
DIFF="$(git diff "${BASE_SHA}...${HEAD_SHA}" -- \
        src pom.xml .github docker-compose.yml Dockerfile 2>/dev/null || true)"

if [ -z "${DIFF}" ]; then
  echo "无相关代码改动，跳过 AI 审查。"
  exit 0
fi

TRUNCATED=""
if [ "${#DIFF}" -gt "${MAX_DIFF_CHARS}" ]; then
  DIFF="${DIFF:0:${MAX_DIFF_CHARS}}"
  TRUNCATED=$'\n\n（diff 过长已截断，仅审查前 '"${MAX_DIFF_CHARS}"' 字符）'
fi

# 评审提示词：结合本项目规范（统一响应/异常、DTO 不泄漏实体、Flyway、密钥不入库等）
read -r -d '' SYSTEM_PROMPT <<'EOF' || true
你是社团官网后端（Spring Boot 3.2.1 / Java 17 / MyBatis-Plus / MySQL / Redis / RabbitMQ）的资深代码评审员。
只针对给出的 diff 审查，用简体中文输出。重点关注：
1. 正确性与潜在 bug（空指针、并发、边界、事务边界、资源泄漏）。
2. 安全：鉴权/越权、SQL 注入、密钥硬编码（严禁真实密钥入库）、日志泄漏敏感信息。
3. 本项目规范：Controller 只编排、业务下沉 Service；异常抛 BusinessException 交全局处理，别手写 try/catch 返回错误；出参用 DTO/VO，不直接返回持久层实体（尤其别带 password）；表结构变更必须走 Flyway 且幂等。
4. 简洁性/可维护性、明显性能问题。
输出格式：按「🔴必须修复 / 🟡建议 / 🟢小问题」分组，每条给出文件与行号（若能判断）和简短理由。没有问题就明确说通过。不要逐行复述 diff，只讲有价值的发现。控制篇幅。
EOF

USER_CONTENT="以下是本次 PR 的 diff，请评审：${TRUNCATED}"$'\n\n```diff\n'"${DIFF}"$'\n```'

# 用 jq 构造请求体（OpenAI 兼容格式），避免转义问题
REQ_BODY="$(jq -n \
  --arg model "$MODEL" \
  --arg system "$SYSTEM_PROMPT" \
  --arg user "$USER_CONTENT" \
  '{model:$model, max_tokens:4000, stream:false,
    messages:[{role:"system", content:$system},{role:"user", content:$user}]}')"

RESP="$(curl -sS "${BASE_URL}/chat/completions" \
  -H "content-type: application/json" \
  -H "authorization: Bearer ${DEEPSEEK_API_KEY}" \
  -d "${REQ_BODY}")"

# 提取文本；出错则输出原始响应便于排查
REVIEW="$(echo "${RESP}" | jq -r '.choices[0].message.content // empty' 2>/dev/null || true)"
if [ -z "${REVIEW}" ]; then
  echo "AI 审查调用异常，原始响应："
  echo "${RESP}"
  # 不因审查失败而让整个 PR 检查红掉（审查是辅助）
  exit 0
fi

COMMENT="🤖 **AI 代码审查**（模型：\`${MODEL}\`，仅供参考，需人工复核）"$'\n\n'"${REVIEW}"

# 回帖到 PR
echo "${COMMENT}" | gh pr comment "${PR_NUMBER}" --body-file -
echo "AI 审查已回帖到 PR #${PR_NUMBER}"
