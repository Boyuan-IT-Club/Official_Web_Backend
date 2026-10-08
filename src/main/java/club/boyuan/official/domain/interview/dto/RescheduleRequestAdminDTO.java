package club.boyuan.official.domain.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 改期申请（管理端列表用）。
 * <p>
 * 原先这个接口直接返回实体，管理员看到的是一张 ID 表：requestId、resumeId、
 * 还有 "11,12,13" 这样的 preferred_time_slot_ids。谁申请的看不出来，想排到
 * 什么时候也看不懂——处理一条申请得先去别的页面查两次 ID。
 * 这个 DTO 把需要的东西一次给齐：人是谁、现在排在什么时候、他想换到哪。
 */
@Data
@Builder
public class RescheduleRequestAdminDTO {

    private Integer requestId;

    /** 学生姓名 */
    private String name;

    /** 学号（= 用户名） */
    private String studentId;

    /** 申请理由，学生原话 */
    private String reason;

    /** 当前被排在什么时候；还没排或已取消时为空 */
    private LocalDateTime currentInterviewTime;

    /** 当前面试地点（场次所在教室） */
    private String currentLocation;

    /** 期望时间窗，已解析成人话 */
    private List<PreferredSlot> preferredSlots;

    private LocalDateTime submittedAt;

    /** 0=待处理 1=已同意 2=已拒绝 */
    private Integer status;

    private String adminNote;

    private LocalDateTime handledAt;

    /**
     * 同意之后这条安排是不是还等着重排。
     * 同意改期只会取消原安排，真正换到哪一场得管理员再操作一次——
     * 没有这个标记，管理员点完「同意」就以为结束了。
     */
    private boolean awaitingReassign;

    @Data
    @Builder
    public static class PreferredSlot {
        private Integer timeSlotId;
        /** 如「10-11 周六上午 09:00-11:00」，直接显示给人看 */
        private String label;
    }
}
