#!/usr/bin/env bash
# AI 辅助生成单元测试草稿：给定被测类名，读取源码 + 现有测试风格，调用 DeepSeek 产出 JUnit5+Mockito 测试，
# 写入 src/test 对应路径，并开一个 Draft PR 供人工审核。
# 依赖环境变量：
#   DEEPSEEK_API_KEY  —— DeepSeek 密钥（GitHub Secret）
#   GITHUB_TOKEN      —— Actions 注入，用于 gh 建 PR
#   TARGET_CLASS      —— 被测类简单名（如 SessionAssignmentServiceImpl）或全限定名
# 可选：
#   REVIEW_MODEL / DEEPSEEK_BASE_URL / MAX_SRC_CHARS
set -euo pipefail

MODEL="${REVIEW_MODEL:-deepseek-chat}"
BASE_URL="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}"
MAX_SRC_CHARS="${MAX_SRC_CHARS:-16000}"

if [ -z "${TARGET_CLASS:-}" ]; then
  echo "未提供 TARGET_CLASS"; exit 1
fi
if [ -z "${DEEPSEEK_API_KEY:-}" ]; then
  echo "未配置 DEEPSEEK_API_KEY，跳过。"; exit 0
fi

SIMPLE_NAME="${TARGET_CLASS##*.}"
SRC_FILE="$(find src/main/java -type f -name "${SIMPLE_NAME}.java" | head -n1 || true)"
if [ -z "${SRC_FILE}" ]; then
  echo "未在 src/main/java 找到 ${SIMPLE_NAME}.java"; exit 1
fi
echo "被测源文件：${SRC_FILE}"

PKG="$(grep -m1 '^package ' "${SRC_FILE}" | sed -E 's/package[[:space:]]+([^;]+);/\1/' | tr -d '[:space:]')"
PKG_PATH="$(echo "${PKG}" | tr '.' '/')"
TEST_DIR="src/test/java/${PKG_PATH}"
TEST_FILE="${TEST_DIR}/${SIMPLE_NAME}Test.java"
if [ -f "${TEST_FILE}" ]; then
  echo "测试已存在：${TEST_FILE}（本流程只生成缺失的测试草稿）"; exit 0
fi

SRC_CONTENT="$(cat "${SRC_FILE}")"
if [ "${#SRC_CONTENT}" -gt "${MAX_SRC_CHARS}" ]; then
  SRC_CONTENT="${SRC_CONTENT:0:${MAX_SRC_CHARS}}"
fi

# 取一个现有测试作为风格参考
REF_TEST="$(find src/test/java -type f -name '*Test.java' | head -n1 || true)"
REF_CONTENT=""
[ -n "${REF_TEST}" ] && REF_CONTENT="$(head -c 6000 "${REF_TEST}")"

read -r -d '' SYSTEM_PROMPT <<'EOF' || true
你是 Spring Boot 3.2.1 / Java 17 项目的资深测试工程师。请为给定的被测类生成**可编译、可独立运行**的 JUnit 5 单元测试。
硬性要求：
1. 使用 JUnit 5（org.junit.jupiter）+ Mockito（@ExtendWith(MockitoExtension.class) / @Mock / @InjectMocks），外部依赖（DB/Redis/MQ/飞书/Mapper）一律 Mock，禁止启动 Spring 容器（除非必须，尽量纯单测）。
2. 只输出**一个完整的 Java 测试类源码**，包含正确的 package 与 import；不要输出任何解释文字、不要 Markdown 以外的内容。
3. 覆盖：正常路径 + 关键边界/异常分支；断言要具体（避免只断言非空）。
4. 命名 <类名>Test，遵循给出的现有测试风格。不要臆造不存在的方法/字段；不确定处给出最合理的可编译实现，可用 // TODO 标注需人工确认之处。
只输出被 ```java 包裹的代码块。
EOF

USER_CONTENT="被测类包名：${PKG}
被测类源码：
\`\`\`java
${SRC_CONTENT}
\`\`\`
"
if [ -n "${REF_CONTENT}" ]; then
  USER_CONTENT="${USER_CONTENT}
现有测试风格参考：
\`\`\`java
${REF_CONTENT}
\`\`\`"
fi

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

CONTENT="$(echo "${RESP}" | jq -r '.choices[0].message.content // empty' 2>/dev/null || true)"
if [ -z "${CONTENT}" ]; then
  echo "AI 调用异常，原始响应："; echo "${RESP}"; exit 1
fi

# 提取 ```java ... ``` 代码块；没有围栏则直接用全文
CODE="$(printf '%s' "${CONTENT}" | awk '/```/{f=!f; next} f')"
[ -z "${CODE}" ] && CODE="${CONTENT}"

mkdir -p "${TEST_DIR}"
printf '%s\n' "${CODE}" > "${TEST_FILE}"
echo "已生成：${TEST_FILE}"

# 开 Draft PR
BRANCH="ai/test-${SIMPLE_NAME}-${GITHUB_RUN_ID:-manual}"
git config user.name "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"
git checkout -b "${BRANCH}"
git add "${TEST_FILE}"
git commit -m "test: AI 生成 ${SIMPLE_NAME} 单元测试草稿（待人工审核）"
git push -u origin "${BRANCH}"

gh pr create --draft \
  --title "test(AI草稿): ${SIMPLE_NAME}Test" \
  --body "🤖 由 AI 测试生成工作流产出的**测试草稿**（模型：\`${MODEL}\`）。

- 被测类：\`${PKG}.${SIMPLE_NAME}\`
- 生成文件：\`${TEST_FILE}\`

⚠️ **必须人工审核**：核对断言真实性、删除幻觉、补齐边界、确保 \`./mvnw test\` 通过后再合并。" \
  --base "${PR_BASE:-develop}" || echo "gh pr create 失败（可能分支保护/权限），分支已推送：${BRANCH}"
