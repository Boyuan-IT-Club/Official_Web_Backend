#!/usr/bin/env python3
"""
秒杀容量压测：阶梯加压找最大 QPS / 并发拐点。
- 每阶段前 reset（SQL + Redis）
- 每用户每阶段仅 1 次请求（避免周期锁干扰 QPS）
- 输出 status 分布与 Prometheus 快照
"""
from __future__ import annotations

import argparse
import json
import subprocess
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

DEFAULT_BASE = "http://localhost:8080"
DEFAULT_PASSWORD = "loadtest123"
CYCLE_ID = 99
SLOT_ID = 9901
MYSQL = ["mysql", "-h127.0.0.1", "-P3307", "-uroot", "-proot", "official"]
# 应用 loadtest profile 连宿主机 Redis（与 docker exec official-redis 可能不是同一实例）
DEFAULT_REDIS = ["redis-cli", "-h", "127.0.0.1"]


@dataclass
class RequestResult:
    status: int
    latency_ms: float


@dataclass
class PhaseResult:
    name: str
    concurrency: int
    duration_sec: float
    total: int = 0
    status_counts: Dict[int, int] = field(default_factory=dict)
    latencies_ms: List[float] = field(default_factory=list)
    wall_sec: float = 0.0

    @property
    def qps(self) -> float:
        return self.total / self.wall_sec if self.wall_sec else 0.0

    @property
    def accepted(self) -> int:
        return self.status_counts.get(202, 0) + self.status_counts.get(200, 0)

    @property
    def errors(self) -> int:
        return sum(c for s, c in self.status_counts.items() if s not in (200, 202))

    def percentile(self, p: float) -> float:
        if not self.latencies_ms:
            return 0.0
        s = sorted(self.latencies_ms)
        return s[min(int(len(s) * p), len(s) - 1)]

    def to_dict(self) -> dict:
        err_rate = (self.errors / self.total * 100) if self.total else 0
        return {
            "name": self.name,
            "concurrency": self.concurrency,
            "total": self.total,
            "accepted_200_202": self.accepted,
            "errors": self.errors,
            "error_rate_pct": round(err_rate, 2),
            "qps": round(self.qps, 1),
            "p50_ms": round(self.percentile(0.50), 1),
            "p95_ms": round(self.percentile(0.95), 1),
            "p99_ms": round(self.percentile(0.99), 1),
            "status_counts": {str(k): v for k, v in sorted(self.status_counts.items())},
            "wall_sec": round(self.wall_sec, 2),
        }


def http_json(method: str, url: str, body: dict | None = None, token: str | None = None) -> Tuple[int, object, float]:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            ms = (time.perf_counter() - start) * 1000
            try:
                return resp.status, json.loads(raw), ms
            except json.JSONDecodeError:
                return resp.status, raw, ms
    except urllib.error.HTTPError as e:
        ms = (time.perf_counter() - start) * 1000
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw), ms
        except json.JSONDecodeError:
            return e.code, raw, ms
    except Exception as ex:
        ms = (time.perf_counter() - start) * 1000
        return 0, str(ex), ms


def login(base: str, email: str, password: str) -> Optional[str]:
    status, payload, _ = http_json(
        "POST", f"{base}/api/auth/login",
        {"auth_type": "email-password", "auth_id": email, "verify": password},
    )
    if status != 200 or not isinstance(payload, dict):
        return None
    data = payload.get("data") or {}
    token = data.get("token")
    if isinstance(token, dict):
        token = token.get("token")
    return token


def load_tokens_parallel(base: str, user_count: int, password: str, workers: int = 32) -> List[Tuple[str, str]]:
    tokens: List[Tuple[str, str]] = []

    def one(i: int) -> Optional[Tuple[str, str]]:
        email = f"loadtest{i:03d}@stu.ecnu.edu.cn"
        t = login(base, email, password)
        return (email, t) if t else None

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futs = [pool.submit(one, i) for i in range(1, user_count + 1)]
        for f in as_completed(futs):
            r = f.result()
            if r:
                tokens.append(r)
    tokens.sort(key=lambda x: x[0])
    return tokens


def reset_env(
    mysql_cmd: List[str],
    slot_id: int,
    reset_sql: str,
    capacity: int = 500,
    redis_cmd: Optional[List[str]] = None,
    drain_sec: float = 5.0,
) -> None:
    redis = redis_cmd or DEFAULT_REDIS
    subprocess.run(
        ["docker", "exec", "rabbitmq", "rabbitmqctl", "purge_queue", "official.interview.booking.persist"],
        check=False,
        capture_output=True,
    )
    with open(reset_sql, encoding="utf-8") as f:
        subprocess.run(mysql_cmd, input=f.read(), text=True, check=True, capture_output=True)
    # 必须清空应用实际连接的 Redis（通常为宿主机 6379），否则 user:cycle 锁 600s 残留
    subprocess.run(redis + ["FLUSHDB"], check=True, capture_output=True)
    subprocess.run(
        redis + ["SET", f"interview:slot:remain:{slot_id}", str(capacity)],
        check=True,
        capture_output=True,
    )
    time.sleep(drain_sec)


def query_db_stats(mysql_cmd: List[str], slot_id: int) -> dict:
    stats = {}
    q1 = subprocess.run(
        mysql_cmd + ["-N", "-e", f"SELECT COUNT(*) FROM interview_schedule WHERE slot_id={slot_id} AND status=1;"],
        capture_output=True, text=True,
    )
    stats["active_schedules"] = int(q1.stdout.strip() or 0)
    q2 = subprocess.run(
        mysql_cmd + ["-N", "-e", f"SELECT current_occupied, max_capacity FROM interview_slot WHERE slot_id={slot_id};"],
        capture_output=True, text=True,
    )
    parts = q2.stdout.strip().split()
    if len(parts) >= 2:
        stats["db_occupied"], stats["max_capacity"] = int(parts[0]), int(parts[1])
    q3 = subprocess.run(
        mysql_cmd + ["-N", "-e", "SELECT COUNT(*) FROM message_outbox WHERE status=1;"],
        capture_output=True, text=True,
    )
    stats["outbox_sent"] = int(q3.stdout.strip() or 0)
    q4 = subprocess.run(
        DEFAULT_REDIS + ["GET", f"interview:slot:remain:{slot_id}"],
        capture_output=True, text=True,
    )
    stats["redis_remain"] = q4.stdout.strip()
    return stats


def run_phase_once_per_user(
    base: str,
    tokens: List[Tuple[str, str]],
    concurrency: int,
    cycle_id: int,
    slot_id: int,
    max_requests: int,
) -> PhaseResult:
    """固定并发池；每个 user 在本阶段最多打 1 次，测真实抢号 QPS。"""
    result = PhaseResult(name=f"{concurrency}并发", concurrency=concurrency, duration_sec=0)
    user_count = min(len(tokens), max_requests)
    barrier = threading.Barrier(min(concurrency, user_count) + 1)  # +1 主线程
    lock = threading.Lock()
    next_user = {"i": 0}
    stop = threading.Event()

    def worker() -> None:
        try:
            barrier.wait(timeout=30)
        except threading.BrokenBarrierError:
            return
        while not stop.is_set():
            with lock:
                if next_user["i"] >= user_count:
                    return
                idx = next_user["i"]
                next_user["i"] += 1
            _, token = tokens[idx]
            status, _, ms = http_json(
                "POST", f"{base}/api/interview/booking/seckill",
                {"cycleId": cycle_id, "slotId": slot_id, "notes": "loadtest"},
                token=token,
            )
            with lock:
                result.total += 1
                result.latencies_ms.append(ms)
                result.status_counts[status] = result.status_counts.get(status, 0) + 1

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    for t in threads:
        t.start()
    wall_start = time.perf_counter()
    try:
        barrier.wait(timeout=30)
    except threading.BrokenBarrierError:
        pass
    for t in threads:
        t.join(timeout=120)
    stop.set()
    result.wall_sec = max(time.perf_counter() - wall_start, 0.001)
    return result


def run_burst_all_users(
    base: str, tokens: List[Tuple[str, str]], cycle_id: int, slot_id: int
) -> PhaseResult:
    """全员同时 1 请求（脉冲），测峰值 QPS。"""
    n = len(tokens)
    result = PhaseResult(name=f"脉冲{n}用户", concurrency=n, duration_sec=0)
    barrier = threading.Barrier(n + 1)
    lock = threading.Lock()

    def one(i: int) -> None:
        barrier.wait(timeout=60)
        _, token = tokens[i]
        status, _, ms = http_json(
            "POST", f"{base}/api/interview/booking/seckill",
            {"cycleId": cycle_id, "slotId": slot_id, "notes": "burst"},
            token=token,
        )
        with lock:
            result.total += 1
            result.latencies_ms.append(ms)
            result.status_counts[status] = result.status_counts.get(status, 0) + 1

    threads = [threading.Thread(target=one, args=(i,)) for i in range(n)]
    for t in threads:
        t.start()
    start = time.perf_counter()
    barrier.wait(timeout=60)
    for t in threads:
        t.join(timeout=120)
    result.wall_sec = max(time.perf_counter() - start, 0.001)
    return result


def print_phase(r: PhaseResult) -> None:
    print(f"\n=== {r.name} (wall {r.wall_sec:.2f}s) ===")
    print(f"总请求: {r.total}  受理(200/202): {r.accepted}  拒绝/错误: {r.errors}")
    print(f"QPS: {r.qps:.1f}  状态分布: {dict(sorted(r.status_counts.items()))}")
    if r.latencies_ms:
        print(f"P50: {r.percentile(0.50):.0f}ms  P95: {r.percentile(0.95):.0f}ms  P99: {r.percentile(0.99):.0f}ms")


def find_knee(phases: List[PhaseResult], p99_limit: float = 800.0) -> dict:
    max_qps = max(phases, key=lambda p: p.qps)
    healthy = [p for p in phases if p.percentile(0.99) <= p99_limit and p.accepted >= p.total * 0.3]
    best_healthy = max(healthy, key=lambda p: p.qps) if healthy else None
    return {
        "peak_qps_phase": max_qps.name,
        "peak_qps": round(max_qps.qps, 1),
        "peak_concurrency": max_qps.concurrency,
        "recommended_phase": best_healthy.name if best_healthy else None,
        "recommended_qps": round(best_healthy.qps, 1) if best_healthy else None,
        "recommended_concurrency": best_healthy.concurrency if best_healthy else None,
        "p99_limit_ms": p99_limit,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=DEFAULT_BASE)
    parser.add_argument("--users", type=int, default=600)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--cycle-id", type=int, default=CYCLE_ID)
    parser.add_argument("--slot-id", type=int, default=SLOT_ID)
    parser.add_argument("--reset-sql", default="loadtest/reset-loadtest.sql")
    parser.add_argument("--concurrency-sweep", default="50,100,200,300,400,500")
    parser.add_argument("--burst-users", type=int, default=500, help="脉冲测试用户数")
    parser.add_argument("--slot-capacity", type=int, default=500)
    parser.add_argument("--burst-sweep", default="", help="仅脉冲: 100,200,300,500")
    parser.add_argument("--tokens-cache", default="loadtest/tokens-cache.json")
    parser.add_argument("--skip-burst", action="store_true")
    parser.add_argument("--redis-host", default="127.0.0.1", help="与 Spring redis.host 一致")
    parser.add_argument("--drain-sec", type=float, default=5.0, help="每阶段 reset 后等待秒数")
    args = parser.parse_args()
    redis_cmd = ["redis-cli", "-h", args.redis_host]

    status, _, _ = http_json("GET", f"{args.base}/actuator/health")
    if status != 200:
        raise SystemExit(f"App not healthy: {status}")

    import os

    tokens: List[Tuple[str, str]] = []
    if os.path.isfile(args.tokens_cache):
        with open(args.tokens_cache, encoding="utf-8") as f:
            cached = json.load(f)
        tokens = [(t["email"], t["token"]) for t in cached.get("tokens", [])]
        print(f"从缓存加载 {len(tokens)} tokens")
    if len(tokens) < args.users // 2:
        print(f"并行登录 {args.users} 用户...")
        tokens = load_tokens_parallel(args.base, args.users, args.password)
        with open(args.tokens_cache, "w", encoding="utf-8") as f:
            json.dump({"tokens": [{"email": e, "token": t} for e, t in tokens]}, f)
    print(f"可用 token: {len(tokens)}")
    if len(tokens) < 100:
        raise SystemExit("用户不足，请先执行 seed-loadtest.sql（600 用户）")

    all_phases: List[PhaseResult] = []

    if args.burst_sweep:
        for n_str in args.burst_sweep.split(","):
            n = int(n_str.strip())
            print(f"\n>>> 脉冲 {n} 用户同时 1 请求")
            reset_env(MYSQL, args.slot_id, args.reset_sql, args.slot_capacity, redis_cmd, args.drain_sec)
            r = run_burst_all_users(args.base, tokens[:n], args.cycle_id, args.slot_id)
            print_phase(r)
            db = query_db_stats(MYSQL, args.slot_id)
            print(f"  DB: {db}")
            all_phases.append(r)
            time.sleep(5)
    elif not args.skip_burst:
        print("\n>>> 脉冲测试：全员同时 1 请求")
        reset_env(MYSQL, args.slot_id, args.reset_sql, args.slot_capacity, redis_cmd, args.drain_sec)
        burst_n = min(args.burst_users, len(tokens))
        burst = run_burst_all_users(args.base, tokens[:burst_n], args.cycle_id, args.slot_id)
        print_phase(burst)
        db = query_db_stats(MYSQL, args.slot_id)
        burst_db = db
        all_phases.append(burst)
        time.sleep(5)

    sweep_results: List[PhaseResult] = []
    if not args.burst_sweep:
        for c_str in args.concurrency_sweep.split(","):
            c = int(c_str.strip())
            print(f"\n>>> 阶梯阶段: 并发={c}（每用户 1 次，reset 后开跑）")
            reset_env(MYSQL, args.slot_id, args.reset_sql, args.slot_capacity, redis_cmd, args.drain_sec)
            r = run_phase_once_per_user(
                args.base, tokens, c, args.cycle_id, args.slot_id,
                max_requests=min(args.slot_capacity, len(tokens)),
            )
            print_phase(r)
            db = query_db_stats(MYSQL, args.slot_id)
            print(f"  DB: {db}")
            sweep_results.append(r)
            all_phases.append(r)
            time.sleep(3)
    else:
        sweep_results = all_phases

    knee = find_knee(sweep_results)
    max_accept_qps = max(sweep_results, key=lambda p: p.accepted / p.wall_sec if p.wall_sec else 0)
    summary = {
        "base": args.base,
        "users": len(tokens),
        "slot_id": args.slot_id,
        "slot_capacity": args.slot_capacity,
        "knee_analysis": knee,
        "peak_accept_qps": round(max_accept_qps.accepted / max_accept_qps.wall_sec, 1) if max_accept_qps.wall_sec else 0,
        "peak_accept_phase": max_accept_qps.name,
        "phases": [p.to_dict() for p in all_phases],
    }
    if not args.skip_burst and not args.burst_sweep:
        summary["burst_db_after"] = burst_db
    elif args.burst_sweep and all_phases:
        summary["burst_db_after"] = query_db_stats(MYSQL, args.slot_id)

    with open("loadtest/results-max.json", "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print("\n========== 容量小结 ==========")
    print(f"峰值受理 QPS(202/200): {summary.get('peak_accept_qps')}")
    print(f"峰值 HTTP QPS: {knee['peak_qps']} ({knee['peak_qps_phase']})")
    if knee["recommended_qps"]:
        print(f"推荐水位 (P99≤{knee['p99_limit_ms']}ms): QPS≈{knee['recommended_qps']}, 并发≈{knee['recommended_concurrency']}")
    print("详细: loadtest/results-max.json")


if __name__ == "__main__":
    main()
