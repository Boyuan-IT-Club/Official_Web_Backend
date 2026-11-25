-- 仅在双节点已升级到 V62、均进入 DUAL_WRITE 后执行。使用独立 mysql 批处理连接，遇错退出。
-- 兼容 LEGACY 老读可随时继续。群码管理短暂停写，普通周期读取继续。
CREATE TEMPORARY TABLE cycle_qr_verify (valid TINYINT NOT NULL CHECK (valid=1)) ENGINE=InnoDB;
SET SESSION innodb_lock_wait_timeout = 5;
SET SESSION lock_wait_timeout = 5;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT cycle_id FROM recruitment_cycle ORDER BY cycle_id FOR UPDATE;
INSERT INTO cycle_qr_verify SELECT NOT EXISTS (
  SELECT 1 FROM recruitment_qr_code q LEFT JOIN department d ON d.dept_id=q.dept_id
  LEFT JOIN recruitment_cycle c ON c.cycle_id=q.cycle_id
  WHERE c.cycle_id IS NULL OR q.qr_type NOT IN ('MAIN_GROUP','MAIN_GROUP_2','QA_GROUP','DEPT') OR TRIM(q.image_url)=''
     OR (q.qr_type='DEPT' AND (q.dept_id<=0 OR d.dept_id IS NULL)) OR (q.qr_type<>'DEPT' AND q.dept_id<>0)
);
UPDATE recruitment_cycle c LEFT JOIN cycle_qr_source q ON q.cycle_id=c.cycle_id
SET c.qr_codes=COALESCE(q.qr_codes,JSON_OBJECT()), c.updated_at=c.updated_at
WHERE NOT (c.qr_codes <=> COALESCE(q.qr_codes,JSON_OBJECT()));
INSERT INTO cycle_qr_verify SELECT NOT EXISTS (
  SELECT 1 FROM recruitment_cycle c LEFT JOIN cycle_qr_source q ON q.cycle_id=c.cycle_id
  WHERE NOT (c.qr_codes <=> COALESCE(q.qr_codes,JSON_OBJECT()))
);
INSERT INTO cycle_qr_verify SELECT NOT EXISTS (
  SELECT 1 FROM (
    SELECT raw_q.*, CASE WHEN qr_type='DEPT' THEN CONCAT('$.DEPT.',JSON_QUOTE(CAST(dept_id AS CHAR)))
                         ELSE CONCAT('$.',JSON_QUOTE(qr_type)) END AS path FROM recruitment_qr_code raw_q
  ) q JOIN recruitment_cycle c ON c.cycle_id=q.cycle_id
  WHERE NOT (JSON_EXTRACT(c.qr_codes,q.path) <=> JSON_OBJECT('id',q.id,'image',q.image_url,'remark',q.remark,
      'createdAt',UNIX_TIMESTAMP(q.created_at),'updatedAt',UNIX_TIMESTAMP(q.updated_at)))
);
COMMIT;
DROP TEMPORARY TABLE cycle_qr_verify;
