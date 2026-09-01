#!/usr/bin/env node
// 评测种子数据:T5 联调用——用真实加密报告模拟模板仓 Actions 推送(ADR-0001)。
// 注入 7 份提交:alice×4(得分递进,供趋势图)、bob、carol、dave。
// 用法: node scripts/seed/seed-evaluation.mjs [BASE_URL]   (默认 http://localhost:8080)
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const BASE = process.argv[2] || 'http://localhost:8080';
const dir = dirname(fileURLToPath(import.meta.url));
const data = JSON.parse(readFileSync(join(dir, 'eval-envelopes.json'), 'utf8'));

let ok = 0;
let fail = 0;
for (const [user, entries] of Object.entries(data)) {
  for (let i = 0; i < entries.length; i++) {
    const b64 = Buffer.from(JSON.stringify(entries[i].envelope)).toString('base64');
    const res = await fetch(`${BASE}/api/public/evaluations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        report: b64,
        github_username: user,
        repository: `github.com/${user}/interview-autograding-template`,
      }),
    });
    const code = res.status;
    const text = await res.text().catch(() => '');
    const id = /"data"\s*:\s*(\d+)/.exec(text)?.[1] ?? '?';
    console.log(`POST ${user}[${i}] -> HTTP ${code} id=${id}`);
    if (code === 200) ok++; else fail++;
  }
}

console.log(`\n注入完成: ${ok} 成功, ${fail} 失败。`);
console.log(`  - 管理端 /evaluations(autograding 菜单):应看到 alice/bob/carol/dave,alice 4 次提交`);
console.log(`  - 用户端:登录账号后 /main/person 绑定 GitHub=alice,再回 /main/evaluations 看 205→265→310→340 趋势`);
console.log(`  - 默认全部"未认领",正好测管理端认领流程`);
process.exit(fail > 0 ? 1 : 0);