package club.boyuan.official.feishu;

/**
 * 飞书多维表格列名（须与表格中字段名称完全一致）。
 */
public final class FeishuBitableColumns {

    public static final String NAME = "姓名";
    /** 录取/分配部门（飞书 → 平台） */
    public static final String ASSIGNED_DEPT = "录取部门";
    /** 兼容旧列名 */
    public static final String DEPARTMENT = "部门";
    public static final String INTENDED_DEPT = "意向部门";
    /** 复选框 */
    public static final String INTERVIEW_PASSED = "面试是否通过";
    public static final String PRESELECT_PASSED = "面试是否通过（预选）";
    public static final String ADJUSTABLE = "是否调剂";
    /** 人员字段（@提及） */
    public static final String DECISION_MAKER = "决定人";
    public static final String GRADE = "年级";
    public static final String MAJOR = "专业";
    public static final String SELF_INTRO = "自我介绍";
    public static final String QUESTION_ONE = "第一类问题";
    public static final String QUESTION_TWO = "第二类问题";
    public static final String QUESTION_THREE = "第三类问题";
    public static final String EVALUATION = "面试评价";
    public static final String RESUME_SCORE = "简历评分";
    public static final String PRESELECT = "预选";
    public static final String RECORDER = "记录人";

    private FeishuBitableColumns() {
    }
}
