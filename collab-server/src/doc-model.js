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

/**
 * 每个维度自己的文字评语键，形如 dim:12:note。
 *
 * 原先整行只有一个 comment 总评框，面试官得把四个维度的话揉进一段里，
 * 事后也分不清哪句针对哪一项、是谁写的。改成每维度一格后，
 * 署名天然落到维度级——writer-tracker 本来就按单元格记录写入者。
 */
export function dimensionNoteColId(dimensionId) {
  return `${DIMENSION_COL_PREFIX}${DIMENSION_SEPARATOR}${dimensionId}${DIMENSION_SEPARATOR}${NOTE_SUFFIX}`;
}

const NOTE_SUFFIX = 'note';

/** 解析评分格：dim:12 → 12；dim:12:note 不是评分格，返回 null */
function parseDimensionColId(colId) {
  const parts = colId.split(DIMENSION_SEPARATOR);
  return parts[0] === DIMENSION_COL_PREFIX && parts.length === 2 ? Number(parts[1]) : null;
}

/** 解析评语格：dim:12:note → 12；其余返回 null */
function parseDimensionNoteColId(colId) {
  const parts = colId.split(DIMENSION_SEPARATOR);
  return parts[0] === DIMENSION_COL_PREFIX && parts.length === 3 && parts[2] === NOTE_SUFFIX
    ? Number(parts[1])
    : null;
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

/** 列定义的字段比对：options 这类对象走 JSON，其余按值比 */
function sameColumnValue(current, next) {
  if (current === next) {
    return true;
  }
  if (typeof current === 'object' || typeof next === 'object') {
    return JSON.stringify(current ?? null) === JSON.stringify(next ?? null);
  }
  return false;
}

/**
 * 列对账：把管理员改过的评分维度同步进正在进行的评价表。
 *
 * 原先只有 seedDoc 写 columns，而 seedDoc 只在「文档还是空的」时跑一次。
 * 文档一旦落过快照，列就被冻在那一刻——管理员在管理端加维度、改名、调权重、删维度，
 * 数据库变了、汇总接口变了，唯独面试官眼前的表一动不动（线上报的就是这个）。
 *
 * 原地改而不是整列删了重建：重建会让正在看表的人那一列闪一下，
 * 也会丢掉别人同一时刻对这一列的并发改动。
 *
 * 已填的分数不动。维度被删时只撤掉列，行里的 dim:<id> 单元格留着——
 * 面试官写过的东西不该因为管理员删了一列就凭空消失，
 * 后端算加权总分时本来就会忽略不在维度表里的分数。
 */
export function reconcileColumns(doc, seed) {
  const desired = buildColumns(seed);
  const desiredById = new Map(desired.map((column) => [column.id, column]));
  const columns = doc.getArray('columns');

  let added = 0;
  let updated = 0;
  let removed = 0;

  // 先正序扫一遍决定去留，再倒序删：Y.Array 删掉一项后面的下标就前移，
  // 边遍历边删会漏掉紧随其后的那一项
  const kept = new Set();
  const dropIndices = [];
  for (let i = 0; i < columns.length; i += 1) {
    const item = columns.get(i);
    const id = item instanceof Y.Map ? String(item.get('id')) : null;
    // 丢弃三类：维度已被删除、同一个 id 的重复列（两端并发补列的产物）、非 Y.Map 的脏数据
    if (id === null || !desiredById.has(id) || kept.has(id)) {
      dropIndices.push(i);
      continue;
    }
    kept.add(id);
  }
  for (let i = dropIndices.length - 1; i >= 0; i -= 1) {
    columns.delete(dropIndices[i], 1);
    removed += 1;
  }

  for (let i = 0; i < columns.length; i += 1) {
    const item = columns.get(i);
    const want = desiredById.get(String(item.get('id')));
    let touched = false;
    for (const [key, value] of Object.entries(want)) {
      if (!sameColumnValue(item.get(key), value)) {
        item.set(key, value);
        touched = true;
      }
    }
    if (touched) {
      updated += 1;
    }
  }

  // 新增的维度追加到末尾。前端按 order 字段排序，与数组物理顺序无关
  for (const want of desired) {
    if (kept.has(want.id)) {
      continue;
    }
    const map = new Y.Map();
    Object.entries(want).forEach(([key, value]) => map.set(key, value));
    columns.push([map]);
    added += 1;
  }

  return { added, updated, removed };
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
  info.set('location', row.location);
  info.set('resumeScore', row.resumeScore);
  info.set('resumeScoredByName', row.resumeScoredByName);
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
function ensureSharedCells(rowMap, dimensionIds = []) {
  if (!(rowMap.get(COMMENT_COL) instanceof Y.Text)) {
    rowMap.set(COMMENT_COL, new Y.Text());
  }
  // 每个维度的评语格同样要提前建好：两个客户端同时在空格子里创建 Y.Text
  // 会各建一个，合并时只留下一个，另一人刚敲的字直接消失
  for (const dimensionId of dimensionIds) {
    const key = dimensionNoteColId(dimensionId);
    if (!(rowMap.get(key) instanceof Y.Text)) {
      rowMap.set(key, new Y.Text());
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

    const dimensionIds = (seed.columns ?? []).map((d) => d.dimensionId);
    const rows = doc.getMap('rows');
    for (const row of seed.rows) {
      const rowMap = new Y.Map();
      rows.set(String(row.scheduleId), rowMap);
      writeRowInfo(rowMap, row);
      ensureSharedCells(rowMap, dimensionIds);
    }
  }, 'seed');
}

/**
 * 名单与列的对账：人工调剂或改期之后，把新增候选人补进文档、把已移除的标灰；
 * 管理员改过评分维度时，把列定义一并追平（见 reconcileColumns）。
 *
 * 移除采用标灰而非删除——面试官可能已经写了评价，硬删会连同已填内容一起丢掉。
 */
export function reconcileDoc(doc, seed) {
  let added = 0;
  let removed = 0;
  let columns = { added: 0, updated: 0, removed: 0 };

  doc.transact(() => {
    const meta = doc.getMap('meta');
    meta.set('locked', Boolean(seed.locked));
    // 面试官绑定可能被管理员改动，对账时一并刷新姓名对照表
    meta.set('interviewerNames', seed.interviewerNames ?? {});

    // 列先于行对账：新增维度要先有列，后面 ensureSharedCells 再给每行补上它的评语格
    columns = reconcileColumns(doc, seed);

    const dimensionIds = (seed.columns ?? []).map((d) => d.dimensionId);
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
      // 维度可能被管理员新增，对账时为新维度补建评语格
      ensureSharedCells(rowMap, dimensionIds);
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

  return { added, removed, columns };
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
      // 每维度评语与其作者：{dimensionId: text} / {dimensionId: userId}
      dimensionNotes: {},
      dimensionWriters: {},
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
      const noteDimensionId = parseDimensionNoteColId(cellKey);
      if (dimensionId !== null) {
        if (value !== null && value !== undefined && value !== '') {
          item.scores[dimensionId] = Number(value);
          // 谁打的这个分：单元格粒度，供管理端展示「这一项是谁评的」
          const writer = writers.get(`${rowKey}/${cellKey}`);
          if (writer !== undefined) {
            item.dimensionWriters[dimensionId] = writer;
          }
        }
      } else if (noteDimensionId !== null) {
        if (value !== null && value !== undefined && value !== '') {
          item.dimensionNotes[noteDimensionId] = value;
          const writer = writers.get(`${rowKey}/${cellKey}`);
          if (writer !== undefined) {
            // 评语的作者优先于分数的作者：写字的人比打分的人更能代表这条评价
            item.dimensionWriters[noteDimensionId] = writer;
          }
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
      && Object.keys(item.dimensionNotes).length === 0
      && !item.comment
      && item.recommendation === null;
    if (!untouched) {
      items.push(item);
    }
  }

  return { docName: `eval-board:${cycleId}`, cycleId, items };
}
