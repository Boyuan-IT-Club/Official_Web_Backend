import assert from 'node:assert/strict';
import { test } from 'node:test';
import * as Y from 'yjs';
import {
  COMMENT_COL,
  RECOMMENDATION_COL,
  STATUS_COL,
  dimensionColId,
  dimensionNoteColId,
  materializeFromDoc,
  reconcileDoc,
  seedDoc,
} from '../src/doc-model.js';
import { createWriterTracker } from '../src/writer-tracker.js';

const CYCLE_ID = 3;

function seedPayload(rows) {
  return {
    docName: `eval-board:${CYCLE_ID}`,
    cycleId: CYCLE_ID,
    locked: false,
    columns: [
      { dimensionId: 1, name: '技术能力', maxScore: 10, weight: 2.0, sortOrder: 1 },
      { dimensionId: 2, name: '沟通表达', maxScore: 10, weight: 1.0, sortOrder: 2 },
    ],
    rows,
  };
}

function row(scheduleId, interviewerUserIds) {
  return {
    scheduleId,
    resumeId: scheduleId + 900,
    userId: scheduleId + 800,
    candidateName: `候选人${scheduleId}`,
    account: `2024${scheduleId}`,
    deptId: 1,
    deptName: '技术部',
    sessionId: 10,
    interviewTime: '2026-09-01T10:00:00',
    interviewerUserIds,
  };
}

function newSeededDoc(rows = [row(100, [7, 9])]) {
  const doc = new Y.Doc();
  seedDoc(doc, seedPayload(rows));
  return doc;
}

test('播种后行列结构与候选人快照就位', () => {
  const doc = newSeededDoc();

  const columns = doc.getArray('columns').toArray().map((c) => c.get('id'));
  assert.deepEqual(columns, [
    dimensionColId(1), dimensionColId(2), COMMENT_COL, RECOMMENDATION_COL,
  ]);

  const rowMap = doc.getMap('rows').get('100');
  assert.equal(rowMap.get('_info').get('candidateName'), '候选人100');
  assert.equal(rowMap.get('_info').get('removed'), false);
  // 评语格预建为 Y.Text，避免并发创建时互相覆盖
  assert.ok(rowMap.get(COMMENT_COL) instanceof Y.Text);
});

test('一个候选人只物化出一条评价', () => {
  const doc = newSeededDoc();
  const rowMap = doc.getMap('rows').get('100');

  rowMap.set(dimensionColId(1), 8);
  rowMap.set(dimensionColId(2), 6);
  rowMap.get(COMMENT_COL).insert(0, '基础扎实');
  rowMap.set(RECOMMENDATION_COL, 1);
  rowMap.set(STATUS_COL, 2);

  const { items, docName, cycleId } = materializeFromDoc(doc, CYCLE_ID);

  assert.equal(docName, 'eval-board:3');
  assert.equal(cycleId, CYCLE_ID);
  assert.equal(items.length, 1);
  assert.deepEqual(items[0].scores, { 1: 8, 2: 6 });
  assert.equal(items[0].comment, '基础扎实');
  assert.equal(items[0].recommendation, 1);
  assert.equal(items[0].status, 2);
  assert.equal(items[0].scheduleId, 100);
});

test('几位面试官接力填同一行，署名合并为参与人列表', () => {
  const doc = newSeededDoc();
  const tracker = createWriterTracker(doc);
  const rowMap = doc.getMap('rows').get('100');

  rowMap.set(dimensionColId(1), 8);
  tracker.commit(7);
  rowMap.get(COMMENT_COL).insert(0, '补充：表达清晰');
  tracker.commit(9);

  const { items } = materializeFromDoc(doc, CYCLE_ID, tracker);

  assert.equal(items.length, 1);
  assert.deepEqual(items[0].contributors, [7, 9]);
  // 最后动手的人用于展示「最近更新者」
  assert.equal(items[0].lastEditedBy, 9);
});

test('定稿人取自实际点下定稿的那位面试官', () => {
  const doc = newSeededDoc();
  const tracker = createWriterTracker(doc);
  const rowMap = doc.getMap('rows').get('100');

  rowMap.set(dimensionColId(1), 8);
  tracker.commit(7);
  rowMap.set(STATUS_COL, 2);
  tracker.commit(9);

  const { items } = materializeFromDoc(doc, CYCLE_ID, tracker);

  assert.equal(items[0].status, 2);
  assert.equal(items[0].submittedBy, 9);
});

test('未定稿时不记定稿人', () => {
  const doc = newSeededDoc();
  const tracker = createWriterTracker(doc);
  doc.getMap('rows').get('100').set(dimensionColId(1), 8);
  tracker.commit(7);

  const { items } = materializeFromDoc(doc, CYCLE_ID, tracker);

  assert.equal(items[0].status, 1);
  assert.equal(items[0].submittedBy, null);
});

test('一个字都没填的行不产生物化条目', () => {
  const doc = newSeededDoc([row(100, [7]), row(101, [7])]);
  doc.getMap('rows').get('100').set(dimensionColId(1), 8);

  const { items } = materializeFromDoc(doc, CYCLE_ID);

  assert.equal(items.length, 1);
  assert.equal(items[0].scheduleId, 100);
});

test('标灰的行不再物化', () => {
  const doc = newSeededDoc();
  const rowMap = doc.getMap('rows').get('100');
  rowMap.set(dimensionColId(1), 8);
  rowMap.get('_info').set('removed', true);

  const { items } = materializeFromDoc(doc, CYCLE_ID);

  assert.equal(items.length, 0);
});

test('对账新增行、标灰移除行，且不动已填评价', () => {
  const doc = newSeededDoc([row(100, [7]), row(101, [7])]);
  doc.getMap('rows').get('100').set(dimensionColId(1), 9);

  // 101 被人工调剂走，新排进来 102
  const result = reconcileDoc(doc, seedPayload([row(100, [7]), row(102, [7])]));

  assert.equal(result.added, 1);
  assert.equal(result.removed, 1);
  assert.equal(doc.getMap('rows').get('101').get('_info').get('removed'), true);
  assert.equal(doc.getMap('rows').get('102').get('_info').get('candidateName'), '候选人102');
  // 已填的评分必须原样保留
  assert.equal(doc.getMap('rows').get('100').get(dimensionColId(1)), 9);
});

test('对账刷新面试官绑定，前端据此判断谁能编辑这一行', () => {
  const doc = newSeededDoc([row(100, [7])]);

  reconcileDoc(doc, seedPayload([row(100, [7, 12])]));

  const rowMap = doc.getMap('rows').get('100');
  assert.deepEqual(rowMap.get('_info').get('interviewerUserIds'), [7, 12]);
  // 已存在的评语格不会被对账重建，否则会丢内容
  assert.ok(rowMap.get(COMMENT_COL) instanceof Y.Text);
});

test('服务端播种与对账的写入不算在任何人头上', () => {
  const doc = newSeededDoc();
  const tracker = createWriterTracker(doc);

  reconcileDoc(doc, seedPayload([row(100, [7, 9]), row(103, [7])]));
  tracker.discard();

  assert.equal(tracker.writers.size, 0);
  assert.equal(tracker.contributorsByRow.size, 0);
});

test('两位面试官同时编辑同一格评语时字符级合并互不覆盖', () => {
  const local = newSeededDoc();
  const remote = new Y.Doc();
  Y.applyUpdate(remote, Y.encodeStateAsUpdate(local));

  local.getMap('rows').get('100').get(COMMENT_COL).insert(0, '基础扎实');
  remote.getMap('rows').get('100').get(COMMENT_COL).insert(0, '沟通顺畅');

  Y.applyUpdate(local, Y.encodeStateAsUpdate(remote));
  Y.applyUpdate(remote, Y.encodeStateAsUpdate(local));

  const merged = local.getMap('rows').get('100').get(COMMENT_COL).toString();
  assert.ok(merged.includes('基础扎实'));
  assert.ok(merged.includes('沟通顺畅'));
  // 两个副本必须收敛到完全一致
  assert.equal(merged, remote.getMap('rows').get('100').get(COMMENT_COL).toString());
});

test('两人同时改同一个评分格，收敛到同一个值', () => {
  const local = newSeededDoc();
  const remote = new Y.Doc();
  Y.applyUpdate(remote, Y.encodeStateAsUpdate(local));

  local.getMap('rows').get('100').set(dimensionColId(1), 8);
  remote.getMap('rows').get('100').set(dimensionColId(1), 9);

  Y.applyUpdate(local, Y.encodeStateAsUpdate(remote));
  Y.applyUpdate(remote, Y.encodeStateAsUpdate(local));

  assert.equal(
    local.getMap('rows').get('100').get(dimensionColId(1)),
    remote.getMap('rows').get('100').get(dimensionColId(1)),
  );
});


// ---- 评分维度改动必须同步到正在进行的评价表 ----
// 线上报的 bug：管理端改了评分维度，面试评价表纹丝不动。
// 原因是列只在 seedDoc（文档为空时）写一次，reconcileDoc 从不碰 columns，
// 文档一旦落过快照，列就被永久冻结。

function seedWithColumns(columns, rows = [row(100, [7, 9])]) {
  return { ...seedPayload(rows), columns };
}

function columnIds(doc) {
  return doc.getArray('columns').toArray().map((c) => c.get('id'));
}

function columnByIdOf(doc, id) {
  return doc.getArray('columns').toArray().find((c) => c.get('id') === id);
}

test('对账时新增的维度会补成新列，并给每行预建评语格', () => {
  const doc = newSeededDoc();

  reconcileDoc(doc, seedWithColumns([
    { dimensionId: 1, name: '技术能力', maxScore: 10, weight: 2.0, sortOrder: 1 },
    { dimensionId: 2, name: '沟通表达', maxScore: 10, weight: 1.0, sortOrder: 2 },
    { dimensionId: 5, name: '团队协作', maxScore: 20, weight: 1.5, sortOrder: 3 },
  ]));

  assert.ok(columnIds(doc).includes(dimensionColId(5)), '新维度必须出现在列里');
  const added = columnByIdOf(doc, dimensionColId(5));
  assert.equal(added.get('label'), '团队协作');
  assert.equal(added.get('maxScore'), 20);
  assert.equal(added.get('weight'), 1.5);
  assert.equal(added.get('type'), 'score');
  // 评语格要提前建好，否则两人同时首次输入会各建一个 Y.Text，合并时丢字
  assert.ok(doc.getMap('rows').get('100').get(dimensionNoteColId(5)) instanceof Y.Text);
});

test('改名/改满分/改权重就地生效，已填的分数不受影响', () => {
  const doc = newSeededDoc();
  doc.getMap('rows').get('100').set(dimensionColId(1), 8);

  const result = reconcileDoc(doc, seedWithColumns([
    { dimensionId: 1, name: '能力（技术+学习+思维）', maxScore: 20, weight: 3.0, sortOrder: 1 },
    { dimensionId: 2, name: '沟通表达', maxScore: 10, weight: 1.0, sortOrder: 2 },
  ]));

  const renamed = columnByIdOf(doc, dimensionColId(1));
  assert.equal(renamed.get('label'), '能力（技术+学习+思维）');
  assert.equal(renamed.get('maxScore'), 20);
  assert.equal(renamed.get('weight'), 3.0);
  assert.equal(result.columns.updated, 1);
  assert.equal(result.columns.added, 0);
  assert.equal(result.columns.removed, 0);
  assert.equal(doc.getMap('rows').get('100').get(dimensionColId(1)), 8, '改列不该动已填的分数');
});

test('删除维度只撤列，行里已填的分数留着', () => {
  const doc = newSeededDoc();
  const rowMap = doc.getMap('rows').get('100');
  rowMap.set(dimensionColId(2), 6);

  reconcileDoc(doc, seedWithColumns([
    { dimensionId: 1, name: '技术能力', maxScore: 10, weight: 2.0, sortOrder: 1 },
  ]));

  assert.deepEqual(columnIds(doc), [dimensionColId(1), COMMENT_COL, RECOMMENDATION_COL]);
  // 面试官写过的东西不因为管理员删了一列就消失；后端算加权总分时会忽略它
  assert.equal(rowMap.get(dimensionColId(2)), 6);
});

test('评语列与推荐意见列跟着维度数量后移，始终排在最后', () => {
  const doc = newSeededDoc();

  reconcileDoc(doc, seedWithColumns([
    { dimensionId: 1, name: '技术能力', maxScore: 10, weight: 2.0, sortOrder: 1 },
    { dimensionId: 2, name: '沟通表达', maxScore: 10, weight: 1.0, sortOrder: 2 },
    { dimensionId: 5, name: '团队协作', maxScore: 10, weight: 1.0, sortOrder: 3 },
  ]));

  const ordered = doc.getArray('columns').toArray()
    .slice()
    .sort((a, b) => a.get('order') - b.get('order'))
    .map((c) => c.get('id'));
  assert.deepEqual(ordered, [
    dimensionColId(1), dimensionColId(2), dimensionColId(5), COMMENT_COL, RECOMMENDATION_COL,
  ]);
});

test('重复列被去重：两端并发补同一个维度不会留下两列', () => {
  const doc = newSeededDoc();
  // 模拟管理端本地补列与服务端对账撞在一起，同一个 id 被 push 了两次
  const dup = new Y.Map();
  dup.set('id', dimensionColId(2));
  dup.set('dimensionId', 2);
  dup.set('label', '沟通表达');
  dup.set('type', 'score');
  dup.set('order', 2);
  doc.getArray('columns').push([dup]);

  reconcileDoc(doc, seedPayload([row(100, [7, 9])]));

  const ids = columnIds(doc);
  assert.equal(ids.filter((id) => id === dimensionColId(2)).length, 1, '同一个维度只能留一列');
});

test('维度没动时对账不写任何一列，避免无谓的文档更新', () => {
  const doc = newSeededDoc();
  const result = reconcileDoc(doc, seedPayload([row(100, [7, 9])]));
  assert.deepEqual(result.columns, { added: 0, updated: 0, removed: 0 });
});
