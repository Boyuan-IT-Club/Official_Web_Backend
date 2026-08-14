import * as Y from 'yjs';

/**
 * Y.Doc 结构（与前端绑定层、Java 物化接口三方共同约定）：
 *
 *   meta:    Y.Map    { cycleId, locked, seededAt, interviewerNames }
 *   columns: Y.Array<Y.Map>  列定义
 *   rows:    Y.Map<scheduleId, Y.Map>
 *              ├─ _info                  候选人只读快照（服务端播种与刷新）
 *              ├─ '<colId>'              共享单元格（公共备注）
 *              └─ '<colId>:<userId>'     scoped 单元格，键上带面试官
 *
 * scoped 列每位面试官各有一格，UI 只把自己那格渲染为可编辑；
 * 真正的越权拦截在物化时由 Java 依据 origin 完成，见 materializeFromDoc。
 */

const SCOPED_SUFFIX_SEPARATOR = ':';

/** 评语列：用 Y.Text 承载，支持两人同时编辑同一格的字符级合并 */
export const COMMENT_COL = 'comment';
/** 推荐意见列：1倾向通过 2待定 3不倾向 */
export const RECOMMENDATION_COL = 'recommendation';
/** 提交状态：1草稿 2已提交 */
export const STATUS_COL = 'status';
/** 全员共享的公共备注 */
export const NOTES_COL = 'notes';

/** 评分维度列的键前缀，形如 dim:12 */
const DIMENSION_COL_PREFIX = 'dim';

export function dimensionColId(dimensionId) {
  return `${DIMENSION_COL_PREFIX}${SCOPED_SUFFIX_SEPARATOR}${dimensionId}`;
}

function parseDimensionColId(colId) {
  const parts = colId.split(SCOPED_SUFFIX_SEPARATOR);
  return parts[0] === DIMENSION_COL_PREFIX ? Number(parts[1]) : null;
}

function scopedKey(colId, userId) {
  return `${colId}${SCOPED_SUFFIX_SEPARATOR}${userId}`;
}

function buildColumns(seed) {
  const columns = seed.columns.map((dimension, index) => ({
    id: dimensionColId(dimension.dimensionId),
    dimensionId: dimension.dimensionId,
    label: dimension.name,
    type: 'score',
    maxScore: dimension.maxScore,
    weight: Number(dimension.weight),
    scoped: true,
    width: 90,
    order: dimension.sortOrder ?? index + 1,
  }));

  const base = columns.length;
  columns.push({
    id: COMMENT_COL,
    label: '评语',
    type: 'text',
    scoped: true,
    width: 280,
    order: base + 1,
  });
  columns.push({
    id: RECOMMENDATION_COL,
    label: '推荐意见',
    type: 'select',
    scoped: true,
    options: [
      { value: 1, label: '倾向通过' },
      { value: 2, label: '待定' },
      { value: 3, label: '不倾向' },
    ],
    width: 110,
    order: base + 2,
  });
  columns.push({
    id: NOTES_COL,
    label: '公共备注',
    type: 'text',
    scoped: false,
    width: 240,
    order: base + 3,
  });
  return columns;
}

function writeRowInfo(rowMap, row) {
  const info = new Y.Map();
  info.set('scheduleId', row.scheduleId);
  info.set('resumeId', row.resumeId);
  info.set('userId', row.userId);
  info.set('candidateName', row.candidateName);
  info.set('account', row.account);
  info.set('deptId', row.deptId);
  info.set('deptName', row.deptName);
  info.set('sessionId', row.sessionId);
  info.set('interviewTime', row.interviewTime);
  info.set('interviewerUserIds', row.interviewerUserIds ?? []);
  info.set('removed', false);
  rowMap.set('_info', info);
}

/**
 * 为该行的每位面试官预建可编辑单元格。
 *
 * 评语格提前建好 Y.Text 而不是等首次输入时再建，是为了避开一个 CRDT 陷阱：
 * 两个客户端同时在空格子里创建 Y.Text 会各建一个，合并时只留下一个，另一人刚敲的字直接消失。
 */
function ensureInterviewerCells(rowMap, interviewerUserIds) {
  for (const userId of interviewerUserIds ?? []) {
    const commentKey = scopedKey(COMMENT_COL, userId);
    if (!(rowMap.get(commentKey) instanceof Y.Text)) {
      rowMap.set(commentKey, new Y.Text());
    }
  }
}

/**
 * 首次播种：把名单与维度写进空文档。
 */
export function seedDoc(doc, seed) {
  doc.transact(() => {
    const meta = doc.getMap('meta');
    meta.set('cycleId', seed.cycleId);
    meta.set('locked', Boolean(seed.locked));
    meta.set('seededAt', new Date().toISOString());
    meta.set('interviewerNames', seed.interviewerNames ?? {});

    const columns = doc.getArray('columns');
    columns.delete(0, columns.length);
    columns.push(buildColumns(seed).map((column) => {
      const map = new Y.Map();
      Object.entries(column).forEach(([key, value]) => map.set(key, value));
      return map;
    }));

    const rows = doc.getMap('rows');
    for (const row of seed.rows) {
      const rowMap = new Y.Map();
      rows.set(String(row.scheduleId), rowMap);
      writeRowInfo(rowMap, row);
      ensureInterviewerCells(rowMap, row.interviewerUserIds);
      rowMap.set(NOTES_COL, new Y.Text());
    }
  }, 'seed');
}

/**
 * 名单对账：人工调剂或改期之后，把新增候选人补进文档、把已移除的标灰。
 *
 * 移除采用标灰而非删除——面试官可能已经写了评价，硬删会连同已填内容一起丢掉。
 */
export function reconcileDoc(doc, seed) {
  let added = 0;
  let removed = 0;

  doc.transact(() => {
    const meta = doc.getMap('meta');
    meta.set('locked', Boolean(seed.locked));
    // 面试官绑定可能被管理员改动，对账时一并刷新姓名对照表
    meta.set('interviewerNames', seed.interviewerNames ?? {});

    const rows = doc.getMap('rows');
    const seedIds = new Set(seed.rows.map((row) => String(row.scheduleId)));

    for (const row of seed.rows) {
      const key = String(row.scheduleId);
      let rowMap = rows.get(key);
      if (!rowMap) {
        rowMap = new Y.Map();
        rows.set(key, rowMap);
        writeRowInfo(rowMap, row);
        rowMap.set(NOTES_COL, new Y.Text());
        added += 1;
      } else {
        // 候选人快照与面试官绑定可能变化，刷新只读部分，不动已填单元格
        writeRowInfo(rowMap, row);
      }
      ensureInterviewerCells(rowMap, row.interviewerUserIds);
    }

    for (const key of [...rows.keys()]) {
      if (!seedIds.has(key)) {
        const info = rows.get(key)?.get('_info');
        if (info instanceof Y.Map && info.get('removed') !== true) {
          info.set('removed', true);
          removed += 1;
        }
      }
    }
  }, 'reconcile');

  return { added, removed };
}

function cellValue(value) {
  return value instanceof Y.Text ? value.toString() : value;
}

/**
 * 从文档解析出待物化的评价条目：一行 × 一位面试官 = 一条。
 *
 * @param doc      Y.Doc
 * @param cycleId  周期ID
 * @param writers  Map<cellKey, userId>，服务端记录的单元格最后写入者，用于填 originUserId
 */
export function materializeFromDoc(doc, cycleId, writers = new Map()) {
  const rows = doc.getMap('rows');
  const items = [];
  const version = Date.now();

  for (const [rowKey, rowMap] of rows.entries()) {
    if (!(rowMap instanceof Y.Map)) {
      continue;
    }
    const info = rowMap.get('_info');
    if (info instanceof Y.Map && info.get('removed') === true) {
      continue;
    }

    const scheduleId = Number(rowKey);
    const byInterviewer = new Map();

    for (const [cellKey, rawValue] of rowMap.entries()) {
      if (cellKey === '_info' || !cellKey.includes(SCOPED_SUFFIX_SEPARATOR)) {
        continue;
      }
      const separatorIndex = cellKey.lastIndexOf(SCOPED_SUFFIX_SEPARATOR);
      const colId = cellKey.slice(0, separatorIndex);
      const interviewerUserId = Number(cellKey.slice(separatorIndex + 1));
      if (!Number.isInteger(interviewerUserId)) {
        continue;
      }

      if (!byInterviewer.has(interviewerUserId)) {
        byInterviewer.set(interviewerUserId, {
          scheduleId,
          interviewerUserId,
          // 无记录时回退为单元格归属者：该值只可能来自服务端播种或重启前已校验过的快照
          originUserId: writers.get(`${rowKey}/${cellKey}`) ?? interviewerUserId,
          scores: {},
          comment: null,
          recommendation: null,
          status: 1,
          version,
        });
      }
      const item = byInterviewer.get(interviewerUserId);

      // 同一位面试官只要有任一格是别人写的，整条按越权上报，交由 Java 丢弃并记审计
      const writer = writers.get(`${rowKey}/${cellKey}`);
      if (writer !== undefined && writer !== interviewerUserId) {
        item.originUserId = writer;
      }

      const value = cellValue(rawValue);
      const dimensionId = parseDimensionColId(colId);
      if (dimensionId !== null) {
        if (value !== null && value !== undefined && value !== '') {
          item.scores[dimensionId] = Number(value);
        }
      } else if (colId === COMMENT_COL) {
        item.comment = value === '' ? null : value;
      } else if (colId === RECOMMENDATION_COL) {
        item.recommendation = value === null || value === undefined ? null : Number(value);
      } else if (colId === STATUS_COL) {
        item.status = Number(value) === 2 ? 2 : 1;
      }
    }

    for (const item of byInterviewer.values()) {
      const untouched = Object.keys(item.scores).length === 0
        && !item.comment
        && item.recommendation === null;
      if (!untouched) {
        items.push(item);
      }
    }
  }

  return { docName: `eval-board:${cycleId}`, cycleId, items };
}
