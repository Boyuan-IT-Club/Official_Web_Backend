#!/usr/bin/env bash
# 评测种子数据:T5 联调用——用真实加密报告模拟模板仓 Actions 推送(ADR-0001)。
# 会注入 7 份提交:alice×4(得分递进,供趋势图)、bob、carol、dave。
# 用法: ./scripts/seed/seed-evaluation.sh [BASE_URL]   (默认 http://localhost:8080)
set -euo pipefail

BASE="${1:-http://localhost:8080}"
DIR="$(dirname "$0")"
DATA="$DIR/eval-envelopes.json"

if ! command -v jq >/dev/null 2>&1; then
  echo "需要 jq"
  exit 2
fi
if [ ! -f "$DATA" ]; then
  echo "缺少 $DATA"
  exit 2
fi

for user in $(jq -r 'keys[]' "$DATA"); do
  count=$(jq -r --arg u "$user" '.[$u] | length' "$DATA")
  for ((i = 0; i < count; i++)); do
    b64=$(jq -c --arg u "$user" --argjson i "$i" '.[$u][$i].envelope' "$DATA" | base64 -w0)
    repo="github.com/${user}/interview-autograding-template"
    echo -n "POST ${user}[$i] -> "
    curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "$BASE/api/public/evaluations" \
      -H 'Content-Type: application/json' \
      -d "{\"report\":\"$b64\",\"github_username\":\"$user\",\"repository\":\"$repo\"}"
  done
done

echo
echo "注入完成。"
echo "  - 管理端 /evaluations(autograding 菜单):应看到 alice/bob/carol/dave 4 个候选人,alice 4 次提交"
echo "  - 用户端趋势图:登录一个绑定了 github=alice 的账号(/main/person 绑定),/main/evaluations 看 205→265→310→340 折线"
echo "  - 默认这些提交是'未认领'(无绑定账号),正好可测管理端认领流程"