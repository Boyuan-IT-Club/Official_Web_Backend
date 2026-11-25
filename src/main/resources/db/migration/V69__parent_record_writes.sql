-- Switch this view only after both site instances understand parent-only writes.
CREATE SQL SECURITY INVOKER VIEW parent_record_cutover AS SELECT 0 AS enabled;

CREATE SQL SECURITY INVOKER VIEW cycle_qr_entries AS
WITH entries AS (
    SELECT c.cycle_id,k.kind AS qr_type,0 AS dept_id,
           JSON_EXTRACT(c.qr_codes,CONCAT('$.',JSON_QUOTE(k.kind))) AS item
    FROM recruitment_cycle c JOIN JSON_TABLE(JSON_KEYS(c.qr_codes),'$[*]' COLUMNS (kind VARCHAR(32) PATH '$')) k
    WHERE k.kind IN ('MAIN_GROUP','MAIN_GROUP_2','QA_GROUP')
    UNION ALL
    SELECT c.cycle_id,'DEPT',k.dept_id,
           JSON_EXTRACT(c.qr_codes,CONCAT('$.DEPT.',JSON_QUOTE(CAST(k.dept_id AS CHAR)))) AS item
    FROM recruitment_cycle c JOIN JSON_TABLE(JSON_KEYS(c.qr_codes,'$.DEPT'),'$[*]' COLUMNS (dept_id INT PATH '$')) k
)
SELECT e.cycle_id,e.qr_type,e.dept_id,j.id,j.token,j.image_url,j.remark,
       FROM_UNIXTIME(j.created_at) AS created_at,FROM_UNIXTIME(j.updated_at) AS updated_at
FROM entries e JOIN JSON_TABLE(e.item,'$' COLUMNS (
    id INT PATH '$.id', token CHAR(36) PATH '$.token',image_url VARCHAR(1024) PATH '$.image',
    remark VARCHAR(1024) PATH '$.remark',created_at BIGINT PATH '$.createdAt',updated_at BIGINT PATH '$.updatedAt'
)) j;
