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
    'SELECT doc_name, cycle_id, state, locked FROM collab_doc WHERE doc_name = ?',
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
  };
}

/**
 * 写回文档快照。只更新已存在的记录——开表这个动作归 Java 管，
 * 协同服务不该凭一个 WebSocket 连接就凭空造出一张评价表。
 */
export async function saveState(docName, state) {
  const [result] = await pool.execute(
    'UPDATE collab_doc SET state = ? WHERE doc_name = ?',
    [state, docName],
  );
  return result.affectedRows > 0;
}

export async function isLocked(docName) {
  const [rows] = await pool.query('SELECT locked FROM collab_doc WHERE doc_name = ?', [docName]);
  return rows.length > 0 && rows[0].locked === 1;
}

export async function closePool() {
  await pool.end();
}
