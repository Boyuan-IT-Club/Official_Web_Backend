package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协同评价表的播种数据：协同服务在首次加载文档（或定时对账）时拉取，
 * 据此构造 Y.Doc 的 columns 与 rows。
 * <p>
 * 采用「协同服务主动拉取」而非「Java 推送」，是为了让协同服务重启或快照丢失后能自愈，
 * 且 Java 侧不需要知道协同服务的地址。
 */
@Data
public class EvaluationBoardSeedDTO {

    /** 协同文档名，如 eval-board:3 */
    private String docName;

    private Integer cycleId;

    /** 是否已锁定：锁定时协同服务拒绝一切写入 */
    private Boolean locked;

    /** 列定义（评分维度） */
    private List<EvaluationDimensionDTO> columns = new ArrayList<>();

    /** 行（候选人名单快照） */
    private List<RowSeed> rows = new ArrayList<>();

    /**
     * 本表涉及的面试官 {userId: 姓名}。
     * 行里只存 userId，前端要展示「谁评的」就得有这份对照表；一位面试官通常带多个场次，
     * 故放在文档级而非逐行重复。
     */
    private Map<Integer, String> interviewerNames = new LinkedHashMap<>();

    /**
     * 一行 = 一位待面试的候选人，对应 Y.Doc 里 rows 的一个键。
     * 这些字段进入行的 _info 只读快照，由服务端播种与刷新，不由前端编辑。
     */
    @Data
    public static class RowSeed {

        /** 行键：面试安排ID */
        private Integer scheduleId;

        private Integer resumeId;

        private Integer userId;

        /** 候选人姓名 */
        private String candidateName;

        /** 候选人账号（注册用户名，通常为学号） */
        private String account;

        private Integer deptId;

        private String deptName;

        private Integer sessionId;

        /** 面试地点（取自场次），评价表按地点筛选用 */
        private String location;

        /** 简历初筛分（resume.resume_score），面试官打分时参考 */
        private Integer resumeScore;

        /** 简历打分人姓名，仅管理端（resume:audit 等）展示，面试官前端不渲染 */
        private String resumeScoredByName;

        private LocalDateTime interviewTime;

        /**
         * 该场次绑定的面试官用户ID。
         * scoped 评分列按此展开为 '<colId>:<userId>' 键，UI 只允许本人编辑自己的那一列。
         */
        private List<Integer> interviewerUserIds = new ArrayList<>();
    }
}
