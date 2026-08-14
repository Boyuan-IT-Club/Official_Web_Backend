/**
 * 前端契约测试。
 *
 * 前端的绑定层（Official_Web_Frontend/src/pages/EvaluationBoard/collab.ts）直接读写 Y.Doc，
 * 键名是三方口头约定、没有类型系统兜底的地方——一旦哪边改了格式，物化会静默丢数据。
 * 这里按前端的写法造一份文档，跑通物化，把约定钉死。
 */
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
  seedDoc,
} from '../src/doc-model.js';

const CYCLE_ID = 3;
const ME = 7;
const PEER = 9;

/** 前端 collab.ts 里 scopedKey() 的等价实现 */
const scopedKey = (colId, userId) => `${colId}:${userId}`;

function seededDoc() {
  const doc = new Y.Doc();
  seedDoc(doc, {
    docName: `eval-board:${CYCLE_ID}`,
    cycleId: CYCLE_ID,
    locked: false,
    interviewerNames: { [ME]: '张三', [PEER]: '李四' },
    columns: [
      { dimensionId: 1, name: '技术能力', maxScore: 10, weight: 2.0, sortOrder: 1 },
      { dimensionId: 2, name: '沟通表达', maxScore: 10, weight: 1.0, sortOrder: 2 },
    ],
    rows: [{
      scheduleId: 100,
      resumeId: 1000,
      userId: 800,
      candidateName: '候选人A',
      account: '2024001',
      deptId: 1,
      deptName: '技术部',
      sessionId: 10,
      interviewTime: '2026-09-01T10:00:00',
      interviewerUserIds: [ME, PEER],
    }],
  });
  return doc;
}

/** 模拟前端 writeScore / writeComment / writeRecommendation / writeStatus / writeNotes */
function frontendWrites(doc) {
  const rowMap = doc.getMap('rows').get('100');
  doc.transact(() => {
    rowMap.set(scopedKey(dimensionColId(1), ME), 8);
    rowMap.set(scopedKey(dimensionColId(2), ME), 6);
    rowMap.get(scopedKey(COMMENT_COL, ME)).insert(0, '思路清楚');
    rowMap.set(scopedKey(RECOMMENDATION_COL, ME), 1);
    rowMap.set(scopedKey(STATUS_COL, ME), 2);
    rowMap.get(NOTES_COL).insert(0, '会议室临时更换');
  }, 'local');
}

test('前端写下的单元格能被完整物化', () => {
  const doc = seededDoc();
  frontendWrites(doc);

  const { items, docName, cycleId } = materializeFromDoc(doc, CYCLE_ID);
  assert.equal(docName, `eval-board:${CYCLE_ID}`);
  assert.equal(cycleId, CYCLE_ID);

  // 另一位面试官一个字都没填，不该产生条目
  assert.equal(items.length, 1);
  const mine = items[0];
  assert.equal(mine.scheduleId, 100);
  assert.equal(mine.interviewerUserId, ME);
  assert.deepEqual(mine.scores, { 1: 8, 2: 6 });
  assert.equal(mine.comment, '思路清楚');
  assert.equal(mine.recommendation, 1);
  assert.equal(mine.status, 2);
});

test('公共备注是全员共享的，不算进任何人的评价条目', () => {
  const doc = seededDoc();
  frontendWrites(doc);

  const rowMap = doc.getMap('rows').get('100');
  assert.equal(rowMap.get(NOTES_COL).toString(), '会议室临时更换');

  const { items } = materializeFromDoc(doc, CYCLE_ID);
  assert.equal(items.length, 1);
  assert.ok(!('notes' in items[0]));
});

test('清空评分时前端删键，物化结果里该维度随之消失', () => {
  const doc = seededDoc();
  frontendWrites(doc);

  const rowMap = doc.getMap('rows').get('100');
  doc.transact(() => rowMap.delete(scopedKey(dimensionColId(2), ME)), 'local');

  const { items } = materializeFromDoc(doc, CYCLE_ID);
  assert.deepEqual(items[0].scores, { 1: 8 });
});

test('面试官姓名对照表随文档播种，前端据此显示「谁评的」', () => {
  const doc = seededDoc();
  assert.deepEqual(doc.getMap('meta').get('interviewerNames'), { [ME]: '张三', [PEER]: '李四' });
});

test('前端的加权总分算法与 Java 物化侧一致', () => {
  const doc = seededDoc();
  frontendWrites(doc);

  const weights = { 1: 2.0, 2: 1.0 };
  const { items } = materializeFromDoc(doc, CYCLE_ID);
  const total = Object.entries(items[0].scores)
    .reduce((sum, [dimensionId, score]) => sum + score * weights[dimensionId], 0);

  // 8×2 + 6×1，前端 weightedTotal() 与 Java weightedTotal() 都应算出这个数
  assert.equal(total, 22);
});
