#!/usr/bin/env python3
"""Seed cycle-2 Feishu sync test data into local MySQL."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA_FILE = ROOT / "scripts" / "feishu_test_interview_data.tsv"

CYCLE_ID = 2
BASE_USER_ID = 1000
BASE_RESUME_ID = 1000
SLOT_TECH = 2001
SLOT_PROJECT = 2002
SLOT_GENERAL = 2003

PWD_HASH = "$2a$10$UfOo2mtqbSKOA3yB4F4Ci.54uoNzvFMW2VznfkpXKraHL.e9VBWdC"
FEISHU_URL = os.environ.get(
    "FEISHU_TEST_TABLE_URL",
    "https://example.feishu.cn/base/TEST_APP_TOKEN?table=tblTEST0001",
)

NAME_RE = re.compile(r"^[\u4e00-\u9fff·]{2,8}$")
DEPT_RE = re.compile(r"第[12]志愿[：:]\s*([^、,，]+)")
SCORE_RE = re.compile(r"(\d+(?:\.\d+)?)")


@dataclass
class Candidate:
    name: str
    dept_raw: str
    grade: str
    major: str
    self_intro: str
    resume_score: int
    preselect: str
    adjustable: str
    interviewer: str


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def parse_departments(raw: str) -> list[str]:
    raw = (raw or "").strip()
    if not raw:
        return ["技术部"]
    depts = DEPT_RE.findall(raw)
    if depts:
        return [d.strip() for d in depts[:2]]
    parts = re.split(r"[,，、/]", raw)
    cleaned = [p.strip() for p in parts if p.strip()]
    return cleaned[:2] if cleaned else ["技术部"]


def parse_score(text: str) -> float | None:
    if not text:
        return None
    match = SCORE_RE.search(text.strip())
    if not match:
        return None
    try:
        return float(match.group(1))
    except ValueError:
        return None


def infer_resume_score(parts: list[str], name_idx: int) -> int:
    scores: list[float] = []
    for cell in parts[name_idx + 5 : name_idx + 9]:
        value = parse_score(cell)
        if value is not None:
            scores.append(value)
    if not scores:
        return 70
    avg = sum(scores) / len(scores)
    return max(0, min(100, int(round(avg * 10))))


def normalize_grade(grade: str) -> str:
    grade = (grade or "大一").strip()
    if grade in {"大一", "大二", "大三", "大四", "研究生"}:
        return grade
    if "研" in grade:
        return "研究生"
    if "二" in grade:
        return "大二"
    if "三" in grade:
        return "大三"
    return "大一"


def normalize_major(major: str) -> str:
    major = (major or "软件工程").strip()
    mapping = {"SEI": "软件工程", "计算机": "计算机科学与技术", "统计": "统计学"}
    return mapping.get(major, major)


def parse_line(line: str) -> Candidate | None:
    line = line.strip()
    if not line:
        return None
    parts = line.split("\t")
    name_idx = None
    for i, part in enumerate(parts[:6]):
        token = part.strip()
        if NAME_RE.match(token):
            name_idx = i
            break
    if name_idx is None:
        return None

    name = parts[name_idx].strip()
    dept_raw = parts[name_idx + 1].strip() if len(parts) > name_idx + 1 else ""
    grade = parts[name_idx + 2].strip() if len(parts) > name_idx + 2 else "大一"
    major = parts[name_idx + 3].strip() if len(parts) > name_idx + 3 else "软件工程"
    self_intro = parts[name_idx + 4].strip() if len(parts) > name_idx + 4 else ""

    tail = [p.strip() for p in parts if p.strip()]
    preselect = "否"
    adjustable = "否"
    interviewer = ""
    for token in reversed(tail):
        if token.startswith("@"):
            interviewer = token
            break
    yes_no = [t for t in tail if t in {"是", "否"}]
    if len(yes_no) >= 2:
        preselect, adjustable = yes_no[-2], yes_no[-1]
    elif len(yes_no) == 1:
        preselect = yes_no[0]

    return Candidate(
        name=name,
        dept_raw=dept_raw,
        grade=normalize_grade(grade),
        major=normalize_major(major),
        self_intro=self_intro or "（测试数据）",
        resume_score=infer_resume_score(parts, name_idx),
        preselect=preselect,
        adjustable=adjustable,
        interviewer=interviewer,
    )


def slot_for_interviewer(interviewer: str) -> int:
    if any(x in interviewer for x in ("付宇豪", "项子洛", "陈睿")):
        return SLOT_TECH
    if any(x in interviewer for x in ("郭雅颖", "谭心航", "杨承禹", "陈睿")):
        return SLOT_PROJECT
    if any(x in interviewer for x in ("蒋威博", "周晴")):
        return SLOT_PROJECT
    if any(x in interviewer for x in ("邱吉尔", "谭心航")):
        return SLOT_GENERAL
    return SLOT_TECH


def load_candidates() -> list[Candidate]:
    text = DATA_FILE.read_text(encoding="utf-8")
    seen: set[str] = set()
    result: list[Candidate] = []
    for line in text.splitlines():
        candidate = parse_line(line)
        if candidate is None:
            continue
        if candidate.name in seen:
            print(f"skip duplicate name: {candidate.name}", file=sys.stderr)
            continue
        seen.add(candidate.name)
        result.append(candidate)
    return result


def build_sql(candidates: list[Candidate]) -> str:
    lines: list[str] = [
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "-- Feishu sync test seed for recruitment cycle 2",
        f"DELETE FROM interview_schedule WHERE cycle_id = {CYCLE_ID};",
        f"DELETE FROM resume_field_value WHERE resume_id >= {BASE_RESUME_ID};",
        f"DELETE FROM resume WHERE cycle_id = {CYCLE_ID} AND resume_id >= {BASE_RESUME_ID};",
        f"DELETE FROM user_role WHERE user_id >= {BASE_USER_ID};",
        f"DELETE FROM user WHERE user_id >= {BASE_USER_ID};",
        f"DELETE FROM interview_slot WHERE slot_id IN ({SLOT_TECH}, {SLOT_PROJECT}, {SLOT_GENERAL});",
        "",
        f"INSERT INTO interview_slot (slot_id, cycle_id, interview_date, start_time, end_time, location, interview_type, max_capacity, current_occupied, feishu_table_url, status, created_at, updated_at)",
        f"VALUES",
        f" ({SLOT_TECH}, {CYCLE_ID}, '2025-09-27', '09:00:00', '12:00:00', '技术部面试场', 1, 200, 0, '{sql_escape(FEISHU_URL)}', 1, NOW(), NOW()),",
        f" ({SLOT_PROJECT}, {CYCLE_ID}, '2025-09-27', '13:30:00', '17:30:00', '项目部面试场', 1, 200, 0, '{sql_escape(FEISHU_URL)}', 1, NOW(), NOW()),",
        f" ({SLOT_GENERAL}, {CYCLE_ID}, '2025-09-28', '09:00:00', '12:00:00', '综合媒体面试场', 1, 200, 0, '{sql_escape(FEISHU_URL)}', 1, NOW(), NOW());",
        "",
    ]

    for idx, c in enumerate(candidates):
        user_id = BASE_USER_ID + idx
        resume_id = BASE_RESUME_ID + idx
        slot_id = slot_for_interviewer(c.interviewer)
        username = f"feishu_{idx + 1:03d}"
        email = f"{username}@stu.ecnu.edu.cn"
        phone = f"138{idx + 1:08d}"[:11]
        student_id = f"102451{idx + 1:05d}"
        depts = parse_departments(c.dept_raw)
        dept_json = json.dumps(depts, ensure_ascii=False)
        interview_time = f"2025-09-27 {9 + (idx % 8):02d}:{(idx * 7) % 60:02d}:00"

        lines.append(
            f"INSERT INTO user (user_id, username, password, name, email, phone, major, status, is_deleted, create_time, update_time)"
            f" VALUES ({user_id}, '{username}', '{PWD_HASH}', '{sql_escape(c.name)}', '{email}', '{phone}', '{sql_escape(c.major)}', 1, 0, NOW(), NOW());"
        )
        lines.append(
            f"INSERT INTO user_role (user_id, role_id, create_time) VALUES ({user_id}, 4, NOW());"
        )
        lines.append(
            f"INSERT INTO resume (resume_id, user_id, cycle_id, status, resume_score, submitted_at, created_at, updated_at)"
            f" VALUES ({resume_id}, {user_id}, {CYCLE_ID}, 4, {c.resume_score}, NOW(), NOW(), NOW());"
        )

        field_values = {
            4: c.name,
            5: c.major,
            6: email,
            7: phone,
            8: c.grade,
            9: "男",
            10: dept_json,
            11: c.self_intro,
            12: "Java, Python, C++（测试数据）",
            13: "博远招新测试项目经历",
            16: student_id,
            17: c.self_intro,
            18: "希望加入博远社团，参与技术分享与项目实践。",
        }
        for field_id, value in field_values.items():
            lines.append(
                f"INSERT INTO resume_field_value (resume_id, field_id, field_value, created_at, updated_at)"
                f" VALUES ({resume_id}, {field_id}, '{sql_escape(value)}', NOW(), NOW());"
            )

        notes = (
            f"面试官:{c.interviewer or '未知'}; 预选:{c.preselect}; 调剂:{c.adjustable}"
        )
        lines.append(
            f"INSERT INTO interview_schedule (resume_id, cycle_id, slot_id, interview_time, status, notes, sync_status, notif_status, created_at, updated_at)"
            f" VALUES ({resume_id}, {CYCLE_ID}, {slot_id}, '{interview_time}', 1, '{sql_escape(notes)}', 0, 0, NOW(), NOW());"
        )
        lines.append("")

    slot_counts = {SLOT_TECH: 0, SLOT_PROJECT: 0, SLOT_GENERAL: 0}
    for c in candidates:
        slot_counts[slot_for_interviewer(c.interviewer)] += 1
    for slot_id, count in slot_counts.items():
        lines.append(
            f"UPDATE interview_slot SET current_occupied = {count}, updated_at = NOW() WHERE slot_id = {slot_id};"
        )

    lines.extend(
        [
            "SET FOREIGN_KEY_CHECKS = 1;",
            f"SELECT COUNT(*) AS feishu_test_users FROM user WHERE user_id >= {BASE_USER_ID};",
            f"SELECT COUNT(*) AS feishu_test_resumes FROM resume WHERE resume_id >= {BASE_RESUME_ID};",
            f"SELECT COUNT(*) AS feishu_test_schedules FROM interview_schedule WHERE cycle_id = {CYCLE_ID} AND sync_status = 0;",
        ]
    )
    return "\n".join(lines)


def run_mysql(sql: str) -> None:
    cmd = [
        "docker",
        "exec",
        "-i",
        "official-mysql",
        "mysql",
        "-uroot",
        "-proot",
        "official",
    ]
    proc = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True)
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr.decode("utf-8", errors="replace"))
        raise SystemExit(proc.returncode)
    sys.stdout.write(proc.stdout.decode("utf-8", errors="replace"))


def main() -> None:
    candidates = load_candidates()
    print(f"parsed {len(candidates)} candidates from {DATA_FILE}", file=sys.stderr)
    sql = build_sql(candidates)
    out = ROOT / "scripts" / "seed_feishu_test_data.sql"
    out.write_text(sql, encoding="utf-8")
    print(f"generated {out}", file=sys.stderr)
    run_mysql(sql)
    print("seed completed", file=sys.stderr)


if __name__ == "__main__":
    main()
