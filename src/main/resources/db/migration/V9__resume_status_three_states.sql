-- 简历状态精简为三态：1=草稿 2=已提交 3=已截止（周期结束时仍未提交，由系统派生/落库）
-- 历史上的审核态（3评审中/4通过/5未通过）并入「已提交」——评审结论由 interview_result 承载
UPDATE resume SET status = 2 WHERE status IN (3, 4, 5);
