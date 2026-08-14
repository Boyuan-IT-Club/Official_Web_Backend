import * as Y from 'yjs';

/**
 * Y.Doc 结构（与前端绑定层、Java 物化接口三方共同约定）：
 *
 *   meta:    Y.Map    { cycleId, locked, seededAt, interviewerNames }
 *   columns: Y.Array<Y.Map>  列定义
 *   rows:    Y.Map<scheduleId, Y.Map>
 *              ├─ _info        候选人只读快照（服务端播种与刷新）
 *              └─ '<colId>'    单元格，一行只有一份
 *
 * 协作模式：同场次的几位面试官面同一个候选人，针对一个候选人只有一份评价，
 * 任何一位绑定在该场次上的面试官都可以补充记录、修改分数，并发写同一字段由 CRDT 收敛。
 * 因此单元格键上不带面试官后缀——「谁改过」由服务端的 writer-tracker 旁路记录，
 * 而不是编在键名里（编在键名里的话，客户端可以随便伪造成别人）。
 */

/** 评语列：用 Y.Text 承载，支持多人同时编辑的字符级合并 */
export const COMMENT_COL = 'comment';
/** 推荐意见列（共同结论）：1倾向通过 2待定 3不倾向 */
export const RECOMMENDATION_COL = 'recommendation';
/** 状态：1进行中 2已定稿 */
export const STATUS_COL = 'status';

/** 评分维度列的键前缀，形如 dim:12 */
const DIMENSION_COL_PREFIX = 'dim';
const DIMENSION_SEPARATOR = ':';

export function dimensionColId(dimensionId) {
  return `${DIMENSION_COL_PREFIX}${DIMENSION_SEPARATOR}${dimensionId}`;
}

function parseDimensionColId(colId) {
  const parts = colId.split(DIMENSION_SEPARATOR);
  return parts[0] === DIMENSION_COL_PREFIX && parts.length === 2 ? Number(parts[1]) : null;
}

function buildColumns(seed) {
  const columns = seed.columns.map((dimension, index) => ({
    id: dimensionColId(dimension.dimensionId),
    dimensionId: dimension.dimensionId,
    label: dimension.name,
    type: 'score',
    maxScore: dimension.maxScore,
    weight: Number(dimension.weight),
    width: 90,
    order: dimension.sortOrder ?? index + 1,
  }));

  const base = columns.length;
  columns.push({
    id: COMMENT_COL,
    label: '面试记录与评语',
    type: 'text',
    width: 320,
    order: base + 1,
  });
  columns.push({
    id: RECOMMENDATION_COL,
    label: '推荐意见',
    type: 'select',
    options: [
      { value: 1, label: '倾向通过' },
      { value: 2, label: '待定' },
      { value: 3, label: '不倾向' },
    ],
    width: 110,
    order: base + 2,
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
  // 前端据此判断「我是否负责这场」来决定该行可否编辑
  info.set('interviewerUserIds', row.interviewerUserIds ?? []);
  info.set('removed', false);
  rowMap.set('_info', info);
}

/**
 * 预建评语格。
 *
 * 提前建好 Y.Text 而不是等首次输入时再建，是为了避开一个 CRDT 陷阱：
 * 两个客户端同时在空格子里创建 Y.Text 会各建一个，合并时只留下一个，另一人刚敲的字直接消失。
 */
function ensureSharedCells(rowMap) {
  if (!(rowMap.get(COMMENT_COL) instanceof Y.Text)) {
    rowMap.set(COMMENT_COL, new Y.Text());
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
      ensureSharedCells(rowMap);
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
        added += 1;
      }
      // 候选人快照与面试官绑定可能变化，刷新只读部分，不动已填单元格
      writeRowInfo(rowMap, row);
      ensureSharedCells(rowMap);
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
 * 从文档解析出待物化的评价条目：一行 = 一条。
 *
 * contributors / lastEditedBy / submittedBy 全部取自服务端旁路记录的 tracker，
 * 不信客户端自报——Java 侧还会逐个校验这些人是否绑定在该行所属场次上。
 *
 * @param doc      Y.Doc
 * @param cycleId  周期ID
 * @param tracker  createWriterTracker 的返回值
 */
export function materializeFromDoc(doc, cycleId, tracker = {}) {
  const writers = tracker.writers ?? new Map();
  const contributorsByRow = tracker.contributorsByRow ?? new Map();
  const lastEditorByRow = tracker.lastEditorByRow ?? new Map();

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

    const item = {
      scheduleId: Number(rowKey),
      scores: {},
      comment: null,
      recommendation: null,
      status: 1,
      contributors: [...(contributorsByRow.get(rowKey) ?? [])],
      lastEditedBy: lastEditorByRow.get(rowKey) ?? null,
      submittedBy: null,
      version,
    };

    for (const [cellKey, rawValue] of rowMap.entries()) {
      if (cellKey === '_info') {
        continue;
      }
      const value = cellValue(rawValue);
      const dimensionId = parseDimensionColId(cellKey);
      if (dimensionId !== null) {
        if (value !== null && value !== undefined && value !== '') {
          item.scores[dimensionId] = Number(value);
        }
      } else if (cellKey === COMMENT_COL) {
        item.comment = value === '' ? null : value;
      } else if (cellKey === RECOMMENDATION_COL) {
        item.recommendation = value === null || value === undefined ? null : Number(value);
      } else if (cellKey === STATUS_COL) {
        item.status = Number(value) === 2 ? 2 : 1;
        if (item.status === 2) {
          item.submittedBy = writers.get(`${rowKey}/${STATUS_COL}`) ?? null;
        }
      }
    }

    const untouched = Object.keys(item.scores).length === 0
      && !item.comment
      && item.recommendation === null;
    if (!untouched) {
      items.push(item);
    }
  }

  return { docName: `eval-board:${cycleId}`, cycleId, items };
}
