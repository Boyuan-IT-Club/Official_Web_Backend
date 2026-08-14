/**
 * 端到端联调：按前端的写法连上协同服务，验证共编模型整条链路。
 *
 * 覆盖：两位面试官同时编辑同一位候选人的那份评价 → CRDT 收敛 →
 * 协同服务旁路记录参与人 → 物化回写业务库 → 汇总接口读回署名。
 * 同时验证未绑定该场次的人写入后会被剔除出参与人并留下审计。
 *
 * 需要本地已起：MySQL(3307)、后端(8080)、协同服务(3100)，且已导入 seed.sql。
 * 用法：node test/e2e/collab-e2e.mjs
 */
import jwt from 'jsonwebtoken';
import mysql from 'mysql2/promise';
import * as Y from 'yjs';
import { HocuspocusProvider } from '@hocuspocus/provider';
import { WebSocket } from 'ws';
import 'dotenv/config';

const BACKEND = process.env.BACKEND_BASE_URL ?? 'http://localhost:8080';
const COLLAB_WS = `ws://localhost:${process.env.COLLAB_PORT ?? 3100}`;
const CYCLE_ID = 3;
const DOC_NAME = `eval-board:${CYCLE_ID}`;
const SCHEDULE_ID = 5001;

const INTERVIEWER_A = { userId: 7001, username: 'e2e_interviewer_a' };
const INTERVIEWER_B = { userId: 7002, username: 'e2e_interviewer_b' };
const OUTSIDER = { userId: 7003, username: 'e2e_outsider' };

const COMMENT_COL = 'comment';
const RECOMMENDATION_COL = 'recommendation';
const STATUS_COL = 'status';
const dimensionColId = (id) => `dim:${id}`;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const failures = [];
function check(name, condition, detail) {
  if (condition) {
    console.log(`  ✔ ${name}`);
  } else {
    failures.push(`${name}${detail ? `（${detail}）` : ''}`);
    console.log(`  ✖ ${name}${detail ? `：${detail}` : ''}`);
  }
}

/** 造一张与 Java 后端同密钥的令牌，省去登录流程 */
function tokenFor(user, permissions) {
  return jwt.sign(
    { userId: user.userId, roleNames: ['INTERVIEWER'], permissionCodes: permissions },
    process.env.JWT_SECRET,
    { algorithm: 'HS256', subject: user.username, expiresIn: '1h' },
  );
}

const adminToken = tokenFor({ userId: 1, username: 'e2e_admin' }, ['resume:audit', 'interview:evaluate']);

async function api(path, options = {}) {
  const response = await fetch(`${BACKEND}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${options.token ?? adminToken}`,
      ...(options.headers ?? {}),
    },
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || (body && body.code && body.code !== 200)) {
    throw new Error(`${path} 失败：${response.status} ${JSON.stringify(body)}`);
  }
  return body?.data;
}

function connect(user, permissions) {
  const doc = new Y.Doc();
  const provider = new HocuspocusProvider({
    url: COLLAB_WS,
    name: DOC_NAME,
    document: doc,
    token: tokenFor(user, permissions),
    WebSocketPolyfill: WebSocket,
    onAuthenticationFailed: ({ reason }) => {
      failures.push(`用户 ${user.userId} 连接被拒：${reason}`);
    },
  });

  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`用户 ${user.userId} 同步超时`)), 15000);
    provider.on('synced', () => {
      clearTimeout(timer);
      resolve({ doc, provider });
    });
  });
}

const rowOf = (doc) => doc.getMap('rows').get(String(SCHEDULE_ID));

/** mysql2 会把 JSON 列直接解析成数组，但驱动版本不同也可能拿到字符串 */
function jsonArray(value) {
  if (Array.isArray(value)) return value;
  if (typeof value === 'string') return JSON.parse(value || '[]');
  return [];
}

async function main() {
  const db = await mysql.createConnection({
    host: process.env.DB_HOST ?? '127.0.0.1',
    port: Number(process.env.DB_PORT ?? 3307),
    user: process.env.DB_USER ?? 'root',
    password: process.env.DB_PASSWORD ?? 'root',
    database: process.env.DB_NAME ?? 'official',
  });

  // 每次从干净状态开始，避免上一轮的快照与评价干扰断言
  await db.execute('DELETE FROM interview_evaluation WHERE cycle_id = ?', [CYCLE_ID]);
  await db.execute('DELETE FROM collab_audit WHERE doc_name = ?', [DOC_NAME]);
  await db.execute('DELETE FROM collab_doc WHERE doc_name = ?', [DOC_NAME]);

  console.log('\n[1] 管理员开启评价表');
  const board = await api(`/api/interview/evaluation/cycles/${CYCLE_ID}/board`, { method: 'POST' });
  check('开表返回文档名', board?.docName === DOC_NAME, board?.docName);
  check('按有效安排播种出 2 行', board?.rowCount === 2, `rowCount=${board?.rowCount}`);

  console.log('\n[2] 两位面试官连上同一份文档');
  const a = await connect(INTERVIEWER_A, ['interview:evaluate']);
  const b = await connect(INTERVIEWER_B, ['interview:evaluate']);
  check('甲拿到播种后的名单', rowOf(a.doc) !== undefined);
  check('乙拿到同一份名单', rowOf(b.doc) !== undefined);
  check('行上带着本场面试官绑定',
    JSON.stringify(rowOf(a.doc).get('_info').get('interviewerUserIds')) === '[7001,7002]',
    JSON.stringify(rowOf(a.doc).get('_info').get('interviewerUserIds')));

  console.log('\n[3] 甲乙同时编辑同一位候选人的同一份评价');
  a.doc.transact(() => {
    rowOf(a.doc).set(dimensionColId(16), 8);
    rowOf(a.doc).get(COMMENT_COL).insert(0, '甲：基础扎实');
  }, 'local');
  b.doc.transact(() => {
    rowOf(b.doc).set(dimensionColId(17), 7);
    rowOf(b.doc).get(COMMENT_COL).insert(0, '乙：表达清晰。');
  }, 'local');

  await sleep(2000);

  const commentA = rowOf(a.doc).get(COMMENT_COL).toString();
  const commentB = rowOf(b.doc).get(COMMENT_COL).toString();
  check('两人的评语字符级合并、互不覆盖',
    commentA.includes('甲：基础扎实') && commentA.includes('乙：表达清晰。'), commentA);
  check('两端收敛到完全一致', commentA === commentB, `${commentA} / ${commentB}`);
  check('甲能看到乙填的分数', rowOf(a.doc).get(dimensionColId(17)) === 7);
  check('乙能看到甲填的分数', rowOf(b.doc).get(dimensionColId(16)) === 8);

  console.log('\n[4] 乙点下定稿并给出共同结论');
  b.doc.transact(() => {
    rowOf(b.doc).set(RECOMMENDATION_COL, 1);
    rowOf(b.doc).set(STATUS_COL, 2);
  }, 'local');

  console.log('\n[5] 等待物化回写业务库');
  await sleep(4000);

  const [rows] = await db.execute(
    'SELECT * FROM interview_evaluation WHERE cycle_id = ? AND schedule_id = ?', [CYCLE_ID, SCHEDULE_ID]);
  check('一位候选人只落一条评价', rows.length === 1, `实际 ${rows.length} 条`);

  const evaluation = rows[0];
  if (evaluation) {
    const contributors = jsonArray(evaluation.contributors);
    check('参与人署上甲乙两人',
      contributors.includes(7001) && contributors.includes(7002), JSON.stringify(contributors));
    check('加权总分 = 8×2 + 7×1 = 23',
      Number(evaluation.total_score) === 23, String(evaluation.total_score));
    check('评语两人的内容都在库里',
      evaluation.comment?.includes('甲：基础扎实') && evaluation.comment?.includes('乙：表达清晰。'),
      evaluation.comment);
    check('状态为已定稿', evaluation.status === 2, String(evaluation.status));
    check('定稿人记为实际点下的乙', evaluation.submitted_by === 7002, String(evaluation.submitted_by));
    check('定稿时间已记录', evaluation.submitted_at !== null);
  }

  console.log('\n[6] 未绑定该场次的人写入后应被剔除并留审计');
  const outsider = await connect(OUTSIDER, ['interview:evaluate']);
  outsider.doc.transact(() => {
    rowOf(outsider.doc).set(dimensionColId(16), 1);
  }, 'local');
  await sleep(4000);

  const [afterRows] = await db.execute(
    'SELECT * FROM interview_evaluation WHERE cycle_id = ? AND schedule_id = ?', [CYCLE_ID, SCHEDULE_ID]);
  const afterContributors = jsonArray(afterRows[0]?.contributors);
  check('路人没有被署名', !afterContributors.includes(7003), JSON.stringify(afterContributors));
  check('甲乙的署名没有因这一轮被冲掉',
    afterContributors.includes(7001) && afterContributors.includes(7002), JSON.stringify(afterContributors));

  const [audits] = await db.execute(
    'SELECT * FROM collab_audit WHERE doc_name = ? AND user_id = ? AND rejected = 1', [DOC_NAME, 7003]);
  check('越权写入留下 rejected 审计', audits.length > 0, `${audits.length} 条`);

  // 已知取舍：CRDT 收敛后分不出哪个字符是谁写的，无法只回滚越权者的那一笔。
  // 因此值照落（否则一个捣乱者就能让整场记录都写不进去），靠署名剔除 + 审计事后追查。
  check('越权者改动的分值确实留在库里，只能靠审计追查',
    Number(afterRows[0]?.total_score) === 9, String(afterRows[0]?.total_score));

  console.log('\n[7] 汇总接口读回共编结果');
  const summary = await api(`/api/interview/evaluation/cycles/${CYCLE_ID}/summary`);
  const candidate = summary?.candidates?.find((c) => c.scheduleId === SCHEDULE_ID);
  check('汇总里该候选人只有一份评价', candidate !== undefined);
  if (candidate) {
    check('汇总带出参与人姓名',
      candidate.contributors?.some((c) => c.name === '面试官甲')
      && candidate.contributors?.some((c) => c.name === '面试官乙'),
      JSON.stringify(candidate.contributors));
    check('汇总的加权总分与库内一致',
      Number(candidate.totalScore) === Number(afterRows[0]?.total_score), String(candidate.totalScore));
    check('汇总带出本场绑定人数', candidate.assignedInterviewerCount === 2,
      String(candidate.assignedInterviewerCount));
    check('汇总带出定稿人姓名', candidate.submittedByName === '面试官乙', candidate.submittedByName);
  }

  console.log('\n[8] 快照已落库，重启后可恢复');
  const [docs] = await db.execute('SELECT state FROM collab_doc WHERE doc_name = ?', [DOC_NAME]);
  check('collab_doc 存有快照', (docs[0]?.state?.length ?? 0) > 0, `${docs[0]?.state?.length ?? 0} 字节`);

  a.provider.destroy();
  b.provider.destroy();
  outsider.provider.destroy();
  await db.end();

  console.log('\n────────────────────────────');
  if (failures.length === 0) {
    console.log('端到端联调全部通过');
    process.exit(0);
  }
  console.log(`端到端联调失败 ${failures.length} 项：`);
  failures.forEach((item) => console.log(`  - ${item}`));
  process.exit(1);
}

main().catch((error) => {
  console.error('\n端到端联调异常中止：', error);
  process.exit(1);
});
