ALTER TABLE collab_doc
  ADD COLUMN author_state JSON NULL COMMENT '同版 Y.Doc 的服务端署名快照，仅协作服务可写',
  ADD COLUMN snapshot_revision BIGINT NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_collab_author_state CHECK ((author_state IS NULL AND snapshot_revision=0)
    OR (author_state IS NOT NULL AND JSON_SCHEMA_VALID(
    '{"type":"object","required":["rows"],"additionalProperties":false,"properties":{"rows":{"type":"object","additionalProperties":{"type":"object","properties":{"contributors":{"type":"array","uniqueItems":true,"items":{"type":"integer","minimum":1}},"dimensionWriters":{"type":"object","additionalProperties":{"type":"integer","minimum":1}},"cells":{"type":"object","additionalProperties":{"type":"integer","minimum":1}},"lastEditedBy":{"type":["integer","null"],"minimum":1},"submittedBy":{"type":["integer","null"],"minimum":1},"submittedAt":{"type":["string","null"]}}}}}}',author_state)));
