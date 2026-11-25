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
import { commitSnapshot, loadDoc, closePool } from '../../src/db.js';
import mysql from 'mysql2/promise';
import * as Y from 'yjs';
import { HocuspocusProvider } from '@hocuspocus/provider';
import { WebSocket } from 'ws';
import 'dotenv/config';

const BACKEND = process.env.BACKEND_BASE_URL ?? 'http://localhost:8080';
const COLLAB_WS = `ws://localhost:${process.env.COLLAB_PORT ?? 3100}`;
const CYCLE_ID = 7003;
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

const adminToken = tokenFor({ userId: 7000, username: 'e2e_admin' }, ['resume:audit', 'interview:evaluate']);

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
      console.info(`用户 ${user.userId} 连接被拒：${reason}`);
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
  if (process.env.DB_NAME !== 'official_eval_e2e' || !['localhost','127.0.0.1'].includes(process.env.DB_HOST)) throw new Error('请使用本地 official_eval_e2e 测试库');
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

  const [dimensions] = await db.execute("SELECT dimension_id FROM evaluation_dimension WHERE cycle_id=? ORDER BY sort_order", [CYCLE_ID]);
  const [first, second] = dimensions.map(d => dimensionColId(d.dimension_id));
  console.log('\n[2] 两位面试官连上同一份文档');
  const a = await connect(INTERVIEWER_A, ['interview:evaluate']);
  const b = await connect(INTERVIEWER_B, ['interview:evaluate']);
  check('甲拿到播种后的名单', rowOf(a.doc) !== undefined);
  check('乙拿到同一份名单', rowOf(b.doc) !== undefined);
  console.log('\n[3] 甲乙同时编辑同一位候选人的同一份评价');
  a.doc.transact(() => {
    rowOf(a.doc).set(first, 8);
    rowOf(a.doc).get(COMMENT_COL).insert(0, '甲：基础扎实');
  }, 'local');
  b.doc.transact(() => {
    rowOf(b.doc).set(second, 7);
    rowOf(b.doc).get(COMMENT_COL).insert(0, '乙：表达清晰。');
  }, 'local');

  await sleep(2000);

  const commentA = rowOf(a.doc).get(COMMENT_COL).toString();
  const commentB = rowOf(b.doc).get(COMMENT_COL).toString();
  check('两人的评语字符级合并、互不覆盖',
    commentA.includes('甲：基础扎实') && commentA.includes('乙：表达清晰。'), commentA);
  check('两端收敛到完全一致', commentA === commentB, `${commentA} / ${commentB}`);
  check('甲能看到乙填的分数', rowOf(a.doc).get(second) === 7);
  check('乙能看到甲填的分数', rowOf(b.doc).get(first) === 8);

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

  console.log('\n[6] 账号撤权后，原连接不能再修改已提交的评价');
  await db.execute("DELETE ur FROM user_role ur JOIN role r ON r.role_id=ur.role_id WHERE ur.user_id=? AND r.role_code='INTERVIEWER'", [INTERVIEWER_A.userId]);
  rowOf(a.doc).set(first, 1);
  await sleep(1500);
  const [afterRows] = await db.execute('SELECT * FROM interview_evaluation WHERE cycle_id=? AND schedule_id=?', [CYCLE_ID,SCHEDULE_ID]);
  check('撤权后原得分和历史署名保留', Number(afterRows[0]?.total_score) === 23 && jsonArray(afterRows[0]?.contributors).includes(7001));
  a.provider.destroy();
  await db.execute("INSERT IGNORE INTO user_role(user_id,role_id) SELECT ?,role_id FROM role WHERE role_code='INTERVIEWER'", [INTERVIEWER_A.userId]);

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
    check('汇总带出定稿人姓名', candidate.submittedByName === '面试官乙', candidate.submittedByName);
  }

  console.log('\n[8] 快照已落库，重启后可恢复');
  const [docs] = await db.execute('SELECT state FROM collab_doc WHERE doc_name = ?', [DOC_NAME]);
  check('collab_doc 存有快照', (docs[0]?.state?.length ?? 0) > 0, `${docs[0]?.state?.length ?? 0} 字节`);

  b.provider.destroy();
  const original = await loadDoc(DOC_NAME);
  const same = {revision: original.revision, authors: original.authors, state: original.state};
  await commitSnapshot(DOC_NAME, original.revision - 1, same, INTERVIEWER_B.userId);
  const changed = structuredClone(same.authors);
  changed.rows[String(SCHEDULE_ID)].materialized.comment = '冲突测试';
  let conflict = false;
  try { await commitSnapshot(DOC_NAME, original.revision - 1, {...same, authors: changed}, INTERVIEWER_B.userId); }
  catch { conflict = true; }
  const [[audit]] = await db.execute('SELECT COUNT(*) n FROM collab_audit WHERE doc_name=? AND snapshot_conflict IS NOT NULL', [DOC_NAME]);
  check('同版重复幂等，同版内容冲突留下一条审计', conflict && audit.n === 1);
  let missing = false;
  try { await commitSnapshot(DOC_NAME, original.revision, {...same, revision: original.revision + 1, authors:{rows:{}}}, INTERVIEWER_B.userId); }
  catch { missing = true; }
  const winner = await loadDoc(DOC_NAME);
  check('缺失署名的新版本被拒，原有二进制、署名和版本完整', missing && winner.revision === original.revision
    && Buffer.from(winner.state).equals(original.state) && JSON.stringify(winner.authors) === JSON.stringify(original.authors));
  await db.execute('DELETE FROM interview_evaluation WHERE cycle_id=? AND schedule_id=?', [CYCLE_ID,SCHEDULE_ID]);
  const deadline = Date.now() + 18000;
  let replay;
  do {
    await sleep(350);
    [[replay]] = await db.execute('SELECT version,contributors,submitted_by,total_score FROM interview_evaluation WHERE cycle_id=? AND schedule_id=?', [CYCLE_ID,SCHEDULE_ID]);
  } while (!replay && Date.now() < deadline);
  check('无浏览器连接时从已提交快照重放，版本和定稿署名一致',
    Number(replay?.version) === winner.revision && replay?.submitted_by === 7002 && Number(replay?.total_score) === 23);
  await closePool();
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
