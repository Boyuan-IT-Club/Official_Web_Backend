import { isDeepStrictEqual } from 'node:util';
import { createHash } from 'node:crypto';
import { baselineAuthors, validateAuthorChange } from './author-state.js';
import mysql from 'mysql2/promise';
import { config } from './config.js';

const pool = mysql.createPool({
  host: config.db.host,
  port: config.db.port,
  user: config.db.user,
  password: config.db.password,
  database: config.db.database,
  connectionLimit: config.db.connectionLimit,
  waitForConnections: true,
});

/**
 * 读取文档快照。返回 null 表示该周期尚未开表（Java 侧还没建 collab_doc 记录）。
 */
export async function loadDoc(docName) {
  const [rows] = await pool.query(
    'SELECT doc_name, cycle_id, state, locked, snapshot_revision, author_state FROM collab_doc WHERE doc_name = ?',
    [docName],
  );
  if (rows.length === 0) {
    return null;
  }
  const row = rows[0];
  return {
    docName: row.doc_name,
    cycleId: row.cycle_id,
    state: row.state ?? null,
    locked: row.locked === 1,
    revision: Number(row.snapshot_revision),
    authors: row.author_state,
  };
}

export async function baseSnapshot(doc, reader = pool) {
  if (doc.revision > 0) return {authors: doc.authors, base: doc.revision};
  const [history] = await reader.execute(
    `SELECT schedule_id,contributors,dimension_writers,last_edited_by,submitted_by,
            DATE_FORMAT(submitted_at,'%Y-%m-%dT%H:%i:%s') submitted_at,
            scores,dimension_notes,comment,recommendation,status,version
       FROM interview_evaluation WHERE cycle_id=?`, [doc.cycleId]);
  return {authors: baselineAuthors(history), base: Math.max(0, ...history.map(item => Number(item.version)))};
}

function digest(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

export async function commitSnapshot(name, previous, next, actor) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const [[row]] = await conn.execute('SELECT cycle_id,state,author_state,snapshot_revision,locked FROM collab_doc WHERE doc_name=? FOR UPDATE', [name]);
    if (!row || row.locked) throw new Error('评价板已关闭或锁定');
    if (Number(row.snapshot_revision) !== previous) {
      if (Number(row.snapshot_revision) === next.revision && Buffer.from(row.state).equals(next.state)
          && isDeepStrictEqual(row.author_state, next.authors)) {
        await conn.commit();
        return;
      }
      const conflict = {rows: Object.keys(next.authors?.rows ?? {}).filter(key => next.authors.rows[key]?.editedRevision === next.revision), revision: next.revision, committedRevision: Number(row.snapshot_revision),
        committedHash: digest(row.state ?? Buffer.alloc(0)), rejectedHash: digest(next.state),
        rejectedState: next.state.toString('base64'), rejectedAuthors: next.authors,
        committedAuthorsHash: digest(JSON.stringify(row.author_state))};
      await conn.execute('INSERT INTO collab_audit(doc_name,user_id,rejected,snapshot_conflict) VALUES(?,?,1,?)',
        [name,actor,JSON.stringify(conflict)]);
      await conn.commit();
      throw new Error('协作快照版本冲突，已保留首次提交，请重新连接');
    }
    const [[grant]] = await conn.execute(
      "SELECT COUNT(*) n FROM evaluation_account_grant WHERE user_id=? AND permission_code IN ('interview:evaluate','interview:board:manage','resume:audit')", [actor]);
    const baseline = await baseSnapshot({revision: previous, authors: row.author_state, cycleId: row.cycle_id}, conn);
    if (next.revision !== baseline.base + 1) throw new Error('协作版本未连续递增');
    const ids = validateAuthorChange(baseline.authors, next, actor);
    const [eligible] = await conn.query("SELECT schedule_id FROM evaluation_board_candidate WHERE cycle_id=(SELECT cycle_id FROM collab_doc WHERE doc_name=?) AND schedule_id IN (?)", [name, ids]);
    if (!grant.n || !ids.length || eligible.length !== ids.length) throw new Error('评价权限或候选名单已经变更');
    await conn.execute('UPDATE collab_doc SET state=?,author_state=?,snapshot_revision=? WHERE doc_name=?',
      [next.state, JSON.stringify(next.authors), next.revision, name]);
    await conn.commit();
  } catch (error) {
    await conn.rollback();
    throw error;
  } finally {
    conn.release();
  }
}

export async function pendingMaterializations() {
  const [docs] = await pool.execute(`
    SELECT d.doc_name, d.cycle_id, d.snapshot_revision, d.author_state FROM collab_doc d
    WHERE d.snapshot_revision > 0 AND EXISTS (
      SELECT 1 FROM JSON_TABLE(d.author_state,'$.rows.*' COLUMNS (
        edited BIGINT PATH '$.editedRevision', schedule_id INT PATH '$.materialized.scheduleId'
      )) edits JOIN interview_schedule s ON s.schedule_id=edits.schedule_id AND s.cycle_id=d.cycle_id
      LEFT JOIN interview_evaluation e ON e.schedule_id=s.schedule_id
      WHERE edits.edited > COALESCE(e.version,0))
    ORDER BY d.doc_name LIMIT 20`);
  return docs;
}

/**
 * 连通性探测，供 /collab/diag 自检使用。不暴露连接池本身，避免调用方绕过上面的封装。
 */
export async function pingDb() {
  const conn = await pool.getConnection();
  conn.release();
}

export async function closePool() {
  await pool.end();
}
