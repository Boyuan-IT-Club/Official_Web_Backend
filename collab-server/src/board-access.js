import * as decoding from 'lib0/decoding';
import * as Y from 'yjs';

function denied() {
  throw new Error('评价板写入超出当前账号、行或字段的可写范围');
}

// The protocol envelope includes the document name before the Hocuspocus and Yjs message types.
export function incomingUpdate(frame, documentName) {
  const input = decoding.createDecoder(frame);
  if (decoding.readVarString(input) !== documentName) denied();
  const kind = decoding.readVarUint(input);
  if ([1, 3, 7].includes(kind)) return null; // awareness, awareness query, close
  if (kind !== 0 && kind !== 4) denied();
  const sync = decoding.readVarUint(input);
  if (sync === 0) return null; // state-vector request
  if (sync !== 1 && sync !== 2) denied();
  const bytes = decoding.readVarUint8Array(input);
  if (decoding.hasContent(input)) denied();
  return bytes;
}

export function inspectUpdate(document, bytes, access, validated) {
  const clone = new Y.Doc();
  try {
    const rows = clone.getMap('rows');
    const meta = clone.getMap('meta');
    const columns = clone.getArray('columns');
    Y.applyUpdate(clone, Y.encodeStateAsUpdate(document));
    const originalRoots = new Set(['rows', 'meta', 'columns']);
    const limits = new Map(columns.toArray().map(column => [column.get('id'), column.get('maxScore')]));
    const allowedRows = new Set(access.scheduleIds.map(String));
    const allowedScores = new Set(access.dimensionIds.map(id => 'dim:' + id));
    const changed = [];
    let forbidden = false;
    meta.observeDeep(() => { forbidden = true; });
    columns.observeDeep(() => { forbidden = true; });
    rows.observeDeep(events => {
      for (const event of events) {
        const [rowKey, cellKey] = event.path;
        if (!allowedRows.has(rowKey) || ![1, 2].includes(event.path.length)) {
          forbidden = true;
          continue;
        }
        if (event.path.length === 1) {
          for (const key of event.changes.keys.keys()) changed.push([rowKey, key]);
        } else {
          if (!(event.target instanceof Y.Text) || event.delta.some(part => part.attributes || (part.insert && typeof part.insert !== 'string'))) forbidden = true;
          changed.push([rowKey, cellKey]);
        }
      }
    });
    Y.applyUpdate(clone, bytes);
    if (forbidden || clone.store.pendingStructs || clone.store.pendingDs
        || [...clone.share.keys()].some(key => !originalRoots.has(key))) denied();
    if (access.locked && changed.length) denied();
    for (const [row, key] of changed) {
      const value = rows.get(row)?.get(key);
      if (key === 'comment' || (key.endsWith(':note') && allowedScores.has(key.slice(0, -5)))) {
        if (value !== undefined && (!(value instanceof Y.Text)
            || value.toDelta().some(part => typeof part.insert !== 'string' || part.attributes))) denied();
      } else if (allowedScores.has(key)) {
        if (value !== undefined && (typeof value !== 'number' || !Number.isFinite(value)
            || value < 0 || value > (limits.get(key) ?? Infinity))) denied();
      } else if (key === 'status' || key === 'recommendation') {
        if (value !== undefined && !(key === 'status' ? [1, 2] : [1, 2, 3]).includes(value)) denied();
      } else denied();
    }
    return changed.length ? validated(clone, changed) : null;
  } finally {
    clone.destroy();
  }
}
