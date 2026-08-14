import assert from 'node:assert/strict';
import { test } from 'node:test';
import * as Y from 'yjs';
import {
  COMMENT_COL,
  NOTES_COL,
  RECOMMENDATION_COL,
  STATUS_COL,
  dimensionColId,
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
    dimensionColId(1), dimensionColId(2), COMMENT_COL, RECOMMENDATION_COL, NOTES_COL,
  ]);

  const rowMap = doc.getMap('rows').get('100');
  assert.equal(rowMap.get('_info').get('candidateName'), '候选人100');
  assert.equal(rowMap.get('_info').get('removed'), false);
  // 每位面试官的评语格预建为 Y.Text，避免并发创建时互相覆盖
  assert.ok(rowMap.get(`${COMMENT_COL}:7`) instanceof Y.Text);
  assert.ok(rowMap.get(`${COMMENT_COL}:9`) instanceof Y.Text);
});

test('物化提取各面试官的评分、评语与推荐意见', () => {
  const doc = newSeededDoc();
  const rowMap = doc.getMap('rows').get('100');

  rowMap.set(`${dimensionColId(1)}:7`, 8);
  rowMap.set(`${dimensionColId(2)}:7`, 6);
  rowMap.get(`${COMMENT_COL}:7`).insert(0, '基础扎实');
  rowMap.set(`${RECOMMENDATION_COL}:7`, 1);
  rowMap.set(`${STATUS_COL}:7`, 2);

  const { items, docName, cycleId } = materializeFromDoc(doc, CYCLE_ID);

  assert.equal(docName, 'eval-board:3');
  assert.equal(cycleId, CYCLE_ID);
  assert.equal(items.length, 1);
  assert.deepEqual(items[0].scores, { 1: 8, 2: 6 });
  assert.equal(items[0].comment, '基础扎实');
  assert.equal(items[0].recommendation, 1);
  assert.equal(items[0].status, 2);
  assert.equal(items[0].interviewerUserId, 7);
  assert.equal(items[0].scheduleId, 100);
});

test('一个字都没填的面试官不产生物化条目', () => {
  const doc = newSeededDoc();
  doc.getMap('rows').get('100').set(`${dimensionColId(1)}:7`, 8);

  const { items } = materializeFromDoc(doc, CYCLE_ID);

  // 面试官 9 的评语格虽已预建，但没写内容，不该凭空落一条空评价
  assert.equal(items.length, 1);
  assert.equal(items[0].interviewerUserId, 7);
});

test('originUserId 取自服务端记录的实际写入者', () => {
  const doc = newSeededDoc();
  const writers = new Map([['100/' + dimensionColId(1) + ':7', 9]]);
  doc.getMap('rows').get('100').set(`${dimensionColId(1)}:7`, 10);

  const { items } = materializeFromDoc(doc, CYCLE_ID, writers);

  // 用户 9 写了面试官 7 的格子，如实上报，交由 Java 判定越权
  assert.equal(items[0].interviewerUserId, 7);
  assert.equal(items[0].originUserId, 9);
});

test('无写入记录时 originUserId 回退为单元格归属者', () => {
  const doc = newSeededDoc();
  doc.getMap('rows').get('100').set(`${dimensionColId(1)}:7`, 10);

  const { items } = materializeFromDoc(doc, CYCLE_ID);

  assert.equal(items[0].originUserId, 7);
});

test('标灰的行不再物化', () => {
  const doc = newSeededDoc();
  const rowMap = doc.getMap('rows').get('100');
  rowMap.set(`${dimensionColId(1)}:7`, 8);
  rowMap.get('_info').set('removed', true);

  const { items } = materializeFromDoc(doc, CYCLE_ID);

  assert.equal(items.length, 0);
});

test('对账新增行、标灰移除行，且不动已填评价', () => {
  const doc = newSeededDoc([row(100, [7]), row(101, [7])]);
  doc.getMap('rows').get('100').set(`${dimensionColId(1)}:7`, 9);

  // 101 被人工调剂走，新排进来 102
  const result = reconcileDoc(doc, seedPayload([row(100, [7]), row(102, [7])]));

  assert.equal(result.added, 1);
  assert.equal(result.removed, 1);
  assert.equal(doc.getMap('rows').get('101').get('_info').get('removed'), true);
  assert.equal(doc.getMap('rows').get('102').get('_info').get('candidateName'), '候选人102');
  // 已填的评分必须原样保留
  assert.equal(doc.getMap('rows').get('100').get(`${dimensionColId(1)}:7`), 9);
});

test('对账刷新面试官绑定并补建新面试官的评语格', () => {
  const doc = newSeededDoc([row(100, [7])]);

  reconcileDoc(doc, seedPayload([row(100, [7, 12])]));

  const rowMap = doc.getMap('rows').get('100');
  assert.ok(rowMap.get(`${COMMENT_COL}:12`) instanceof Y.Text);
  assert.deepEqual(rowMap.get('_info').get('interviewerUserIds'), [7, 12]);
});

test('写入者追踪把变更归属给 onChange 时给出的用户', () => {
  const doc = newSeededDoc();
  const tracker = createWriterTracker(doc);
  const rowMap = doc.getMap('rows').get('100');

  rowMap.set(`${dimensionColId(1)}:7`, 8);
  tracker.commit(7);

  rowMap.get(`${COMMENT_COL}:9`).insert(0, '表达清晰');
  tracker.commit(9);

  assert.equal(tracker.writers.get(`100/${dimensionColId(1)}:7`), 7);
  assert.equal(tracker.writers.get(`100/${COMMENT_COL}:9`), 9);
});

test('服务端播种与对账的写入不算在任何人头上', () => {
  const doc = newSeededDoc();
  const tracker = createWriterTracker(doc);

  reconcileDoc(doc, seedPayload([row(100, [7, 9]), row(103, [7])]));
  tracker.discard();

  assert.equal(tracker.writers.size, 0);
});

test('两位面试官同时编辑同一格评语时字符级合并互不覆盖', () => {
  const local = newSeededDoc();
  const remote = new Y.Doc();
  Y.applyUpdate(remote, Y.encodeStateAsUpdate(local));

  local.getMap('rows').get('100').get(`${COMMENT_COL}:7`).insert(0, '基础扎实');
  remote.getMap('rows').get('100').get(`${COMMENT_COL}:7`).insert(0, '沟通顺畅');

  Y.applyUpdate(local, Y.encodeStateAsUpdate(remote));
  Y.applyUpdate(remote, Y.encodeStateAsUpdate(local));

  const merged = local.getMap('rows').get('100').get(`${COMMENT_COL}:7`).toString();
  assert.ok(merged.includes('基础扎实'));
  assert.ok(merged.includes('沟通顺畅'));
  // 两个副本必须收敛到完全一致
  assert.equal(merged, remote.getMap('rows').get('100').get(`${COMMENT_COL}:7`).toString());
});
