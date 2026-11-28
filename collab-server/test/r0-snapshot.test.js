import assert from 'node:assert/strict';
import { after, test } from 'node:test';

// This test injects a reader. The pool constructed by db.js never connects.
Object.assign(process.env, {
  DB_HOST: '127.0.0.1', DB_PORT: '1', DB_USER: 'fixture', DB_PASSWORD: 'fixture',
  DB_NAME: 'r0_fixture', JWT_SECRET: 'test-only', COLLAB_SERVICE_TOKEN: 'test-only',
  BACKEND_BASE_URL: 'http://127.0.0.1:1',
});
const { baseSnapshot, closePool } = await import('../src/db.js');
const { parseCycleId } = await import('../src/auth.js');
after(closePool);

test('a historical board uses the latest materialized version for its first new edit', async () => {
  const cycleId = parseCycleId('eval-board:7003');
  const history = [{ schedule_id: 5001, version: 7, contributors: [42], submitted_by: 42 }];
  const reader = { async execute(sql, params) {
    assert.deepEqual(params, [7003]);
    assert.match(sql, /FROM interview_evaluation WHERE cycle_id/);
    return [history];
  }};
  const baseline = await baseSnapshot({ cycleId, revision: 0 }, reader);
  assert.equal(baseline.base + 1, 8);
  assert.deepEqual(baseline.authors.rows['5001'].contributors, [42]);
  const saved = { revision: 8, authors: baseline.authors };
  assert.deepEqual(await baseSnapshot(saved, null), { base: 8, authors: saved.authors });
});
