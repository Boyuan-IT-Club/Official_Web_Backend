-- 兼容期内，从旧群码生成周期 JSON。时间戳保存为 Unix 秒，不依赖回填连接的时区。
CREATE SQL SECURITY INVOKER VIEW cycle_qr_source AS
WITH entries AS (
    SELECT cycle_id, qr_type, dept_id,
           JSON_OBJECT('id', id, 'image', image_url, 'remark', remark,
                       'createdAt', UNIX_TIMESTAMP(created_at), 'updatedAt', UNIX_TIMESTAMP(updated_at)) AS item
    FROM recruitment_qr_code
), groups_by_type AS (
    SELECT cycle_id, qr_type, item FROM entries WHERE qr_type <> 'DEPT'
    UNION ALL
    SELECT cycle_id, 'DEPT', JSON_OBJECTAGG(CAST(dept_id AS CHAR), item)
    FROM entries WHERE qr_type = 'DEPT' GROUP BY cycle_id
)
SELECT cycle_id, JSON_OBJECTAGG(qr_type, CAST(item AS JSON)) AS qr_codes
FROM groups_by_type GROUP BY cycle_id;
