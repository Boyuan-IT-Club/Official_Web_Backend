package club.boyuan.official.domain.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知中心：一届招新里所有对外邮件的收口视图。
 *
 * 分开列而不是合成一张「通知表」，是因为四类通知的主体各不相同——
 * 初筛未通过挂在简历上、面试安排与提醒挂在场次安排上、录取与否挂在结果行上，
 * 触发方式也不同（前两类里只有提醒是系统定时发的）。
 */
@Data
@Builder
public class NotificationCenterDTO {

    /** 简历初筛未通过：待发/已发 */
    private Bucket resumeRejected;

    /** 面试安排通知：排上场次后发出 */
    private Bucket interviewArranged;

    /** 面试前一天提醒（系统定时发，管理端只看不发） */
    private Bucket eveReminder;

    /** 面试当天提醒（系统定时发） */
    private Bucket dayReminder;

    /** 录取 / 未录取结果通知 */
    private Bucket result;

    @Data
    @Builder
    public static class Bucket {
        /** 按当前数据「应该收到这类通知」的人数 */
        private long total;
        /** 已经发过的人数 */
        private long sent;
        /** 还没发的人数 */
        private long pending;
    }

    /** 初筛未通过名单的一行，供通知中心直接勾选发送 */
    @Data
    @Builder
    public static class ScreenedOutItem {
        private Integer resumeId;
        private Integer userId;
        private String name;
        /** 简历里填的学号；没填则为空，不要拿 username 顶替 */
        private String studentId;
        private String email;
        /** 简历平均分；未打过分为 null，与打 0 分区分 */
        private Integer resumeScore;
        /** 最近一次初筛未通过通知的发送时间；为空表示还没通知 */
        private LocalDateTime notifiedAt;
    }

    /** 初筛未通过名单（在 overview 里一并返回，省一次请求） */
    private List<ScreenedOutItem> screenedOut;

    /**
     * 面试安排名单，附三类通知各自发没发。
     *
     * 提醒是系统定时发的，管理端管不着——但「有没有发到」必须看得见：
     * 出过手动改时间后提醒没重发的情况，只给个总数看不出漏了谁。
     */
    @Data
    @Builder
    public static class ScheduleNoticeItem {
        private Integer scheduleId;
        private Integer userId;
        private String name;
        private String studentId;
        private LocalDateTime interviewTime;
        private String deptName;
        private String location;
        /** 面试安排通知是否已发 */
        private boolean arranged;
        /** 前一天提醒是否已发 */
        private boolean eve;
        /** 当天提醒是否已发 */
        private boolean day;
    }

    private List<ScheduleNoticeItem> schedules;
}
