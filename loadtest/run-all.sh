#!/usr/bin/env bash
# 一键压测：初始化 DB → 启动应用(loadtest profile) → 跑阶梯压测 → 校验超卖
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export DB_USERNAME="${DB_USERNAME:-root}"
export DB_PASSWORD="${DB_PASSWORD:-root}"
export JWT_SECRET="${JWT_SECRET:-)fFQ82L8dU]t3C?.HB^;x,xYm9Y<aK21<|K|o0D6(c!X}8[cUZ_l=&0Y('#/K}n_}"
export JAVA_HOME="${JAVA_HOME:-/Users/miaowu/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home}"

echo "==> 1/5 导入 official.sql（若已有数据会重建表）"
docker exec -i official-mysql mysql -uroot -proot official < "$ROOT/src/main/resources/db/official.sql"

echo "==> 2/5 导入压测种子数据"
docker exec -i official-mysql mysql -uroot -proot official < "$ROOT/loadtest/seed-loadtest.sql"

echo "==> 3/5 编译并启动应用 (profile=loadtest)"
./mvnw -q -DskipTests package
pkill -f 'official.*OfficialApplication' 2>/dev/null || true
sleep 2
nohup ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=loadtest \
  > loadtest/app.log 2>&1 &
APP_PID=$!
echo "App PID=$APP_PID, log=loadtest/app.log"

echo "==> 等待健康检查..."
for i in $(seq 1 90); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "App ready after ${i}s"
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "App exited. tail loadtest/app.log:"
    tail -30 loadtest/app.log
    exit 1
  fi
  sleep 2
done

if ! curl -sf http://localhost:8080/actuator/health >/dev/null; then
  echo "App failed to start"
  tail -50 loadtest/app.log
  exit 1
fi

echo "==> 4/5 执行阶梯压测"
python3 "$ROOT/loadtest/run_seckill_loadtest.py" --users 200 --phases "50:20,100:20,150:20"

echo "==> 5/5 业务校验（超卖/占用数）"
docker exec official-mysql mysql -uroot -proot official -N -e "
SELECT CONCAT('slot_max=', max_capacity, ' db_occupied=', current_occupied) FROM interview_slot WHERE slot_id=9901;
SELECT CONCAT('active_schedules=', COUNT(*)) FROM interview_schedule s JOIN interview_slot sl ON s.slot_id=sl.slot_id WHERE sl.slot_id=9901 AND s.status=1;
SELECT CONCAT('outbox_pending=', COUNT(*)) FROM message_outbox WHERE status=0;
SELECT CONCAT('outbox_sent=', COUNT(*)) FROM message_outbox WHERE status=1;
" 2>/dev/null

echo "==> 完成。结果见 loadtest/results.json"
echo "停止应用: kill $APP_PID"
