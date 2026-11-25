import { inspectUpdate, incomingUpdate } from './board-access.js';
import { nextSnapshot } from './author-state.js';
import { Hocuspocus } from '@hocuspocus/server';
import * as Y from 'yjs';
import { config } from './config.js';
import { authenticateToken, parseCycleId } from './auth.js';
import { fetchSeed, materialize, fetchAccess } from './backend-api.js';
import { baseSnapshot, commitSnapshot, loadDoc, pendingMaterializations } from './db.js';
import { reconcileDoc, seedDoc } from './doc-model.js';
import { buildDiag, clearDocError, recordDocError } from './diag.js';

/** 锁定状态缓存的有效期：管理员点锁定后最多这么久就会生效 */
const LOCK_CACHE_TTL_MS = 10000;

/** 每份文档的运行时状态 */
const docStates = new Map();

export function createServer() {
  const hocuspocus = new Hocuspocus({
    port: config.port,
    // Hocuspocus 自带防抖：短时间内的连续编辑合并成一次落库
    debounce: config.storeDebounceMs,
    maxDebounce: config.storeDebounceMs * 5,

    async onAuthenticate({ token, documentName, connection }) {
      const cycleId = parseCycleId(documentName);

      let user;
      try {
        user = authenticateToken(token);
      } catch (error) {
        // 浏览器只会收到 Hocuspocus 统一的 permission-denied，真实原因记进自检供 /collab/diag 回报
        error.noPermission = true;
        recordDocError(documentName, error);
        throw error;
      }

      const access = await fetchAccess(cycleId, token);

      if (access.viewerId !== user.userId) throw new Error("账号身份已变化，请重新登录");
      const doc = await loadDoc(documentName);
      if (!doc) {
        // 评价表由管理员在管理端开启，协同服务不凭一个连接就造表
        const error = new Error(`周期 ${cycleId} 的评价表尚未开启`);
        error.boardNotOpened = true;
        recordDocError(documentName, error);
        throw error;
      }

      // 锁定后所有人只读；已在线的连接由 watchLocks 断开后重连转为只读
      if (doc.locked || access.locked) {
        connection.readOnly = true;
      }

      return { userId: user.userId, username: user.username, isAdmin: access.admin, cycleId, token };
    },

    async beforeHandleMessage({ update, document, documentName, context, connection }) {
      const bytes = incomingUpdate(update, documentName);
      const board = docStates.get(documentName);
      if (!board) throw new Error('评价板尚未载入');
      const apply = async () => {
        const access = await fetchAccess(context.cycleId, context.token);
        if (access.viewerId !== context.userId) throw new Error('账号身份已变化');
        if (access.locked) connection.readOnly = true;
        const roster = new Set(access.scheduleIds.map(String));
        for (const key of document.getMap('rows').keys()) {
          if (!roster.has(key)) document.getMap('rows').delete(key);
        }
        if (!bytes) return;
        if (board.conflicted) throw new Error('协作版本已变化，请重新连接');
        const next = inspectUpdate(document, bytes, access, (snapshot, cells) =>
          nextSnapshot(snapshot, cells, board.authors, context.cycleId, context.userId, board.base + 1));
        if (!next) return;
        try {
          await commitSnapshot(documentName, board.revision, next, context.userId);
        } catch (error) {
          board.conflicted = true;
          hocuspocus.closeConnections(documentName);
          throw error;
        }
        board.authors = next.authors;
        board.base = board.revision = next.revision;
        Y.applyUpdate(document, bytes, connection);
        board.dirty = true;
        scheduleMaterialize(documentName, document);
      };
      const result = board.pending.then(apply);
      board.pending = result.catch(() => {});
      await result;
    },

    async onLoadDocument({ documentName, document }) {
      try {
        return await loadDocumentInner(documentName, document);
      } catch (error) {
        // Hocuspocus 会把这里的任何失败都回报成 permission-denied，
        // 因此必须在抛出前把真实原因留在自检里，否则只能靠翻日志定位
        const code = recordDocError(documentName, error);
        console.error(`[onLoadDocument] ${documentName} 加载失败（${code}）：${error.message}`);
        throw error;
      }
    },


    async onDisconnect({ documentName, document }) {
      // 有人离开时把这份文档的评价即刻落库，避免整场结束后才发现没写进业务库
      await flushMaterialize(documentName, document);
    },

    /**
     * HTTP 自检端点：GET /collab/diag。
     *
     * Hocuspocus 的约定是——钩子以「空错误」reject 表示"本请求我已处理完"，
     * 非空错误会被重新抛出，resolve 则继续走它的默认 200 OK 响应
     * （部署流水线探活用的 GET / 依赖这个默认行为，所以其余路径必须原样 resolve）。
     */
    onRequest({ request, response }) {
      const path = (request.url ?? '').split('?')[0];
      if (path !== '/collab/diag' && path !== '/diag') {
        return Promise.resolve();
      }
      return buildDiag().then((diag) => {
        response.writeHead(200, {
          'Content-Type': 'application/json; charset=utf-8',
          'Cache-Control': 'no-store',
        });
        response.end(JSON.stringify(diag, null, 2));
        return Promise.reject();
      });
    },
  });

  return hocuspocus;
}

/**
 * 文档加载的实际逻辑。单独抽出来，是为了让 onLoadDocument 能在外层统一捕获并归类失败原因
 * （Hocuspocus 会把这里的任何异常都对浏览器回报成 permission-denied）。
 */
async function loadDocumentInner(documentName, document) {
  const cycleId = parseCycleId(documentName);
  const stored = await loadDoc(documentName);
  if (!stored) {
    const error = new Error(`周期 ${cycleId} 的评价表尚未开启`);
    error.boardNotOpened = true;
    throw error;
  }

  const {authors, base} = await baseSnapshot(stored);
  if (stored.state && stored.state.length > 0) {
    Y.applyUpdate(document, new Uint8Array(stored.state), 'restore');
    // 快照恢复后与业务库对账一次，补上停机期间的名单变化
    {
      const seed = await fetchSeed(cycleId);
      const { added, removed, columns } = reconcileDoc(document, seed, authors);
      if (added || removed || columns.added || columns.updated || columns.removed) {
        console.info(`[collab] ${documentName} 恢复后对账：新增 ${added} 行，移除 ${removed} 行，`
          + `列 +${columns.added}/~${columns.updated}/-${columns.removed}`);
      }
    }
  } else {
    const seed = await fetchSeed(cycleId);
    seedDoc(document, seed, authors);
    console.info(`[collab] ${documentName} 首次播种：${seed.rows.length} 行 / ${seed.columns.length} 个评分维度`);
  }

  const state = { cycleId, authors, base, revision: stored.revision, pending: Promise.resolve(),
    materializeTimer: null, dirty: stored.revision > 0, conflicted: false };
  docStates.set(documentName, state);
  if (state.dirty) scheduleMaterialize(documentName, document);
  clearDocError(documentName);

  return document;
}

function scheduleMaterialize(documentName, document) {
  const state = docStates.get(documentName);
  if (!state || state.materializeTimer) {
    return;
  }
  state.materializeTimer = setTimeout(() => {
    state.materializeTimer = null;
    flushMaterialize(documentName, document).catch((error) => {
      console.error(`[collab] ${documentName} 物化失败：${error.message}`);
    });
  }, config.materializeDebounceMs);
}

/**
 * 把文档里的评价回写业务库。Java 侧会逐个校验参与人是否绑定在该场次上，
 * 未绑定者被剔除并记审计，这里只负责如实上报 tracker 记下的参与人。
 */
export async function flushMaterialize(documentName, document) {
  const state = docStates.get(documentName);
  if (!state || !state.dirty) {
    return;
  }
  const revision = state.revision;
  if (!revision || state.conflicted) return;
  const items = Object.values(state.authors.rows).filter(row => row.editedRevision > 0).map(row => row.materialized);
  if (!items.length) return;
  try {
    const result = await materialize({docName: documentName, cycleId: state.cycleId, snapshotRevision: revision, items});
    if (result?.rejected > 0) throw new Error('已提交快照尚未完整物化');
    if (state.revision === revision) state.dirty = false;
  } finally {
    if (state.dirty) scheduleMaterialize(documentName, document);
  }
}

/**
 * 轮询锁定状态：管理员锁表后断开在线连接，客户端重连即转为只读。
 * 直接在消息层拦截会误伤只读浏览与在线状态同步，断开重连是更干净的做法。
 */
export function watchLocks(hocuspocus) {
  return setInterval(async () => {
    for (const documentName of docStates.keys()) {
      try {
        const document = hocuspocus.documents.get(documentName);
        if (!document) continue;
        for (const connection of document.getConnections()) {
          try {
            const {cycleId, token} = connection.context;
            const access = await fetchAccess(cycleId, token);
            if (access.viewerId !== connection.context.userId || access.locked !== Boolean(connection.readOnly)) {
              connection.close();
              continue;
            }
            const roster = new Set(access.scheduleIds.map(String));
            document.transact(() => {
              for (const key of document.getMap('rows').keys()) {
                if (!roster.has(key)) document.getMap('rows').delete(key);
              }
            }, 'roster');
          } catch {
            connection.close();
          }
        }
      } catch (error) {
        console.warn(`[collab] ${documentName} 锁定状态检查失败：${error.message}`);
      }
    }
  }, LOCK_CACHE_TTL_MS);
}

/**
 * 定时与业务库对账，把人工调剂/改期产生的名单变化同步进文档。
 */
export function watchRoster(hocuspocus) {
  return setInterval(async () => {
    for (const [documentName, state] of docStates.entries()) {
      const document = hocuspocus.documents.get(documentName);
      if (!document) {
        continue;
      }
      try {
        const refresh = state.pending.then(async () => reconcileDoc(document, await fetchSeed(state.cycleId), state.authors));
        state.pending = refresh.catch(() => {});
        const { added, removed, columns } = await refresh;

        if (added || removed || columns.added || columns.updated || columns.removed) {
          console.info(`[collab] ${documentName} 名单对账：新增 ${added} 行，移除 ${removed} 行，`
            + `列 +${columns.added}/~${columns.updated}/-${columns.removed}`);
        }
      } catch (error) {
        console.warn(`[collab] ${documentName} 名单对账失败：${error.message}`);
      }
    }
  }, Math.min(config.reconcileIntervalMs, 10000));
}

export function watchMaterializations() {
  let running = false;
  return setInterval(async () => {
    if (running) return;
    running = true;
    try {
      for (const doc of await pendingMaterializations()) {
        const items = Object.values(doc.author_state.rows).filter(row => row.editedRevision > 0).map(row => row.materialized);
        await materialize({docName: doc.doc_name, cycleId: doc.cycle_id, snapshotRevision: Number(doc.snapshot_revision), items});
      }
    } catch (error) {
      console.error(`[collab] 重放已提交评价失败：${error.message}`);
    } finally {
      running = false;
    }
  }, 10000);
}
