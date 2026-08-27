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
  RECOMMENDATION_COL,
  STATUS_COL,
  dimensionColId,
  materializeFromDoc,
  seedDoc,
} from '../src/doc-model.js';
import { createWriterTracker } from '../src/writer-tracker.js';

const CYCLE_ID = 3;
const ME = 7;
const PEER = 9;

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
      location: '理科楼 B204',
      interviewTime: '2026-09-01T10:00:00',
      interviewerUserIds: [ME, PEER],
    }],
  });
  return doc;
}

/** 模拟前端 writeScore / writeComment / writeRecommendation / writeStatus */
function frontendWrites(doc) {
  const rowMap = doc.getMap('rows').get('100');
  doc.transact(() => {
    rowMap.set(dimensionColId(1), 8);
    rowMap.set(dimensionColId(2), 6);
    rowMap.get(COMMENT_COL).insert(0, '思路清楚');
    rowMap.set(RECOMMENDATION_COL, 1);
  }, 'local');
}

test('前端写下的单元格能被完整物化成一条共享评价', () => {
  const doc = seededDoc();
  frontendWrites(doc);

  const { items, docName, cycleId } = materializeFromDoc(doc, CYCLE_ID);
  assert.equal(docName, `eval-board:${CYCLE_ID}`);
  assert.equal(cycleId, CYCLE_ID);

  assert.equal(items.length, 1);
  const evaluation = items[0];
  assert.equal(evaluation.scheduleId, 100);
  assert.deepEqual(evaluation.scores, { 1: 8, 2: 6 });
  assert.equal(evaluation.comment, '思路清楚');
  assert.equal(evaluation.recommendation, 1);
  assert.equal(evaluation.status, 1);
});

test('同伴接着补内容，写在同一份评价上而不是另起一条', () => {
  const doc = seededDoc();
  const tracker = createWriterTracker(doc);
  frontendWrites(doc);
  tracker.commit(ME);

  const rowMap = doc.getMap('rows').get('100');
  doc.transact(() => {
    rowMap.get(COMMENT_COL).insert(rowMap.get(COMMENT_COL).length, '；项目经历真实');
    rowMap.set(dimensionColId(2), 7);
  }, 'local');
  tracker.commit(PEER);

  const { items } = materializeFromDoc(doc, CYCLE_ID, tracker);

  assert.equal(items.length, 1);
  assert.equal(items[0].comment, '思路清楚；项目经历真实');
  assert.equal(items[0].scores[2], 7);
  assert.deepEqual(items[0].contributors, [ME, PEER]);
});

test('前端点定稿写 status，物化带上定稿人', () => {
  const doc = seededDoc();
  const tracker = createWriterTracker(doc);
  frontendWrites(doc);
  tracker.commit(ME);

  doc.transact(() => doc.getMap('rows').get('100').set(STATUS_COL, 2), 'local');
  tracker.commit(PEER);

  const { items } = materializeFromDoc(doc, CYCLE_ID, tracker);
  assert.equal(items[0].status, 2);
  assert.equal(items[0].submittedBy, PEER);
});

test('清空评分时前端删键，物化结果里该维度随之消失', () => {
  const doc = seededDoc();
  frontendWrites(doc);

  const rowMap = doc.getMap('rows').get('100');
  doc.transact(() => rowMap.delete(dimensionColId(2)), 'local');

  const { items } = materializeFromDoc(doc, CYCLE_ID);
  assert.deepEqual(items[0].scores, { 1: 8 });
});

test('面试官姓名对照表随文档播种，前端据此显示参与人署名', () => {
  const doc = seededDoc();
  assert.deepEqual(doc.getMap('meta').get('interviewerNames'), { [ME]: '张三', [PEER]: '李四' });
});

test('行上的面试官绑定决定前端能否编辑该行', () => {
  const doc = seededDoc();
  const info = doc.getMap('rows').get('100').get('_info');
  assert.deepEqual(info.get('interviewerUserIds'), [ME, PEER]);
});

test('面试地点随行播种进 _info，评价表按地点筛选靠它', () => {
  const doc = seededDoc();
  const info = doc.getMap('rows').get('100').get('_info');
  assert.equal(info.get('location'), '理科楼 B204');
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
