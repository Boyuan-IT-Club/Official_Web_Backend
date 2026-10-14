package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 录取统计（GET /api/interview/result/summary）。
 *
 * 原先只有预录取草稿有按部门人数，转正时草稿被删、统计随之清零；正式结果没有任何聚合接口，
 * 「各部门最后录了几个人、其中几个是第一志愿」只能前端自己数。这里由服务端统一算。
 * 口径与结果列表一致：排除初筛未通过且仍待定的人。
 */
@Data
public class ResultSummaryDTO {

    private Totals totals = new Totals();

    private List<DeptRow> departments = new ArrayList<>();

    @Data
    public static class Totals {
        /** 名单总人数 */
        private Integer candidates;
        /** 待定 */
        private Integer pending;
        /** 待调剂（历史取值，按未决定处理） */
        private Integer adjusting;
        private Integer admitted;
        private Integer rejected;
        /** 在预录取名单里、尚未转正的人数 */
        private Integer inPreAdmission;
        /** 已发过结果通知的人数 */
        private Integer notified;
    }

    @Data
    public static class DeptRow {
        private Integer deptId;
        private String deptName;
        /** 第一志愿报了本部门的人数 */
        private Integer firstChoice;
        /** 第二志愿报了本部门的人数 */
        private Integer secondChoice;
        /** 预录取到本部门、尚未转正 */
        private Integer preAdmitted;
        /** 最终录取到本部门 */
        private Integer admitted;
        private Integer admittedFirstChoice;
        private Integer admittedSecondChoice;
        /** 调剂：两个志愿都不是本部门 */
        private Integer admittedOther;
        private Integer admittedNotified;
    }
}
