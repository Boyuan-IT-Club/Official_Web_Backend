import * as Y from 'yjs';
import { materializeFromDoc } from './doc-model.js';
import { isDeepStrictEqual } from 'node:util';

const clone = value => structuredClone(value);

export function baselineAuthors(records) {
  const rows = {};
  for (const r of records) {
    const item = {
      scheduleId: r.schedule_id, contributors: r.contributors ?? [], lastEditedBy: r.last_edited_by,
      submittedBy: r.submitted_by, submittedAt: r.submitted_at, scores: r.scores ?? {},
      dimensionNotes: r.dimension_notes ?? {}, dimensionWriters: r.dimension_writers ?? {},
      comment: r.comment, recommendation: r.recommendation, status: r.status, version: Number(r.version),
    };
    rows[String(r.schedule_id)] = {
      contributors: item.contributors, lastEditedBy: item.lastEditedBy, submittedBy: item.submittedBy,
      submittedAt: item.submittedAt, dimensionWriters: item.dimensionWriters,
      cells: item.submittedBy ? {status: item.submittedBy} : {}, materialized: item,
    };
  }
  return {rows};
}

export function nextSnapshot(document, cells, previous, cycleId, actor, revision) {
  if (!Number.isSafeInteger(actor) || actor < 1 || !Number.isSafeInteger(revision) || revision < 1)
    throw new Error('缺少已认证的评价作者或版本');
  const authors = clone(previous);
  const changed = new Map();
  for (const [row, key] of cells) {
    if (!changed.has(row)) changed.set(row, new Set());
    changed.get(row).add(key);
  }
  const items = materializeFromDoc(document, cycleId, {}, new Set(changed.keys())).items;
  for (const [rowKey, keys] of changed) {
    const projected = items.find(i => String(i.scheduleId) === rowKey);
    if (!projected) throw new Error('本次编辑的行不在物化名单中');
    const row = authors.rows[rowKey] ??= {cells: {}, contributors: [], dimensionWriters: {}};
    const before = row.materialized;
    const item = before ? clone(before) : clone(projected);
    row.lastEditedBy = actor;
    row.contributors = [...new Set([...(row.contributors ?? []), actor])].sort((a,b) => a-b);
    for (const key of keys) {
      row.cells[key] = actor;
      const match = /^dim:(\d+)(:note)?$/.exec(key);
      if (match) {
        const dimension = match[1];
        const field = match[2] ? 'dimensionNotes' : 'scores';
        if (Object.hasOwn(projected[field], dimension)) item[field][dimension] = projected[field][dimension];
        else delete item[field][dimension];
        row.dimensionWriters[dimension] = actor;
      } else if (key === 'status') {
        item.status = projected.status;
        if (before?.status !== projected.status) {
          row.submittedBy = projected.status === 2 ? actor : null;
          row.submittedAt = projected.status === 2 ? new Date().toISOString() : null;
        }
      } else {
        item[key] = projected[key];
      }
    }
    Object.assign(item, {
      contributors: [...row.contributors], lastEditedBy: actor, submittedBy: row.submittedBy ?? null,
      submittedAt: row.submittedAt ?? null, dimensionWriters: {...row.dimensionWriters}, version: revision,
    });
    row.materialized = item;
    row.editedRevision = revision;
  }
  return {state: Buffer.from(Y.encodeStateAsUpdate(document)), authors, revision};
}

// Only cells touched by the authenticated editor may acquire a different author.
export function validateAuthorChange(previous, next, actor) {
  const oldRows = previous?.rows ?? {};
  const rows = next.authors?.rows;
  if (!rows || !Object.keys(rows).length) throw new Error('署名快照缺少行');
  for (const [id, old] of Object.entries(oldRows)) {
    const row = rows[id];
    if (!row || !(old.contributors ?? []).every(person => row.contributors?.includes(person)))
      throw new Error('原有参与人丢失');
    if (row.editedRevision !== next.revision && !isDeepStrictEqual(old, row))
      throw new Error('无编辑来源的行发生变化');
    for (const [key, author] of Object.entries(old.cells ?? {})) {
      if (row.cells?.[key] !== author && row.cells?.[key] !== actor) throw new Error('原单元格署名丢失');
    }
    for (const [key, author] of Object.entries(old.dimensionWriters ?? {})) {
      if (row.dimensionWriters?.[key] !== author && row.dimensionWriters?.[key] !== actor)
        throw new Error('原维度署名丢失');
    }
  }
  const touched = Object.entries(rows).filter(([, r]) => r.editedRevision === next.revision);
  if (!touched.length || touched.some(([id,r]) => r.lastEditedBy !== actor || !r.contributors?.includes(actor)
      || !Object.values(r.cells ?? {}).includes(actor) || r.materialized?.scheduleId !== Number(id)
      || r.materialized?.version !== next.revision)) throw new Error('实际编辑缺少同版署名');
  return touched.map(([id]) => Number(id));
}
