#!/usr/bin/env bash
# 评测 intake 冒烟(T5 验收第 1 步):用自造 curl 精确模拟模板仓 Actions 推送(ADR-0001)。
# 用法: ./scripts/evaluation-intake-smoke.sh [BASE_URL]  (默认 http://localhost:8080)
# 需要后端已启动(MySQL/Redis 就绪)。报告单 envelope 为工具仓真跑产出的固定向量。
set -euo pipefail

BASE="${1:-http://localhost:8080}"
ENV_FILE="$(dirname "$0")/../.smoke-envelope.json"

if [ ! -f "$ENV_FILE" ]; then
  echo "缺少 $ENV_FILE(密文 envelope)。用 .smoke-envelope.json.example 生成。"
  exit 2
fi

REPORT_B64=$(base64 -w0 < "$ENV_FILE")

echo "== 1) 首次推送 =="
R1=$(curl -s -X POST "$BASE/api/public/evaluations" \
  -H 'Content-Type: application/json' \
  -d "{\"report\":\"$REPORT_B64\",\"github_username\":\"alice\"}")
echo "$R1"

echo "== 2) 幂等:同报告重复推送 =="
R2=$(curl -s -X POST "$BASE/api/public/evaluations" \
  -H 'Content-Type: application/json' \
  -d "{\"report\":\"$REPORT_B64\",\"github_username\":\"alice\"}")
echo "$R2"

echo "== 3) 非法载荷(坏 base64)应 400 =="
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "$BASE/api/public/evaluations" \
  -H 'Content-Type: application/json' \
  -d '{"report":"!!!not-base64!!!","github_username":"alice"}'

echo "== 4) 缺 github_username 应 400 =="
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "$BASE/api/public/evaluations" \
  -H 'Content-Type: application/json' \
  -d "{\"report\":\"$REPORT_B64\"}"

echo "== 5) 超频应 429(连发 70 次,阈值 60/60s)=="
for i in $(seq 1 70); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/public/evaluations" \
    -H 'Content-Type: application/json' \
    -d "{\"report\":\"$REPORT_B64\",\"github_username\":\"alice_$i\"}")
  if [ "$CODE" = "429" ]; then
    echo "第 $i 次触发 429"
    break
  fi
done

echo "== 完成:1/2 应返回同一 id,3/4 应 400,5 应出现 429 =="