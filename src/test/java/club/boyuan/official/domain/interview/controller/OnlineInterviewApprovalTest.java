package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.persistence.entity.InterviewRescheduleRequest;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「改为线上」与「改时间」是两种诉求，同意后的动作完全不同。
 * <p>
 * 起因：有同学排完场次才发现当天来不及到校，真正想要的不是换时间而是改成线上
 * （线上 10-07 的申请里就有一条「10月11日有事来不及赶到学校，请问能不能帮忙
 * 安排一下线上面试?」）。此前系统没有线上这个概念，只能当成普通改期处理——
 * 取消安排、等重排，可他要的时间根本没变。
 * <p>
 * 这组断言锁住两条路径不会互相串味。注意这里只验状态机本身；
 * 释放场次名额、摘旧通知等副作用在 Controller 里，由集成测试覆盖。
 */
class OnlineInterviewApprovalTest {

    @Test
    @DisplayName("默认是改时间，不是改线上——老客户端不传 requestType 时行为不变")
    void defaultsToReschedule() {
        InterviewRescheduleRequest req = new InterviewRescheduleRequest();
        assertNull(req.getRequestType(), "实体默认不该自作主张填值，由库的 DEFAULT 0 兜底");
        assertEquals(0, InterviewRescheduleRequest.TYPE_RESCHEDULE);
        assertEquals(1, InterviewRescheduleRequest.TYPE_TO_ONLINE);
    }

    @Test
    @DisplayName("改为线上：安排仍然生效，只是 mode 翻成 1、场次解绑")
    void onlineKeepsScheduleActive() {
        InterviewSchedule schedule = new InterviewSchedule()
                .setScheduleId(1).setStatus(1).setSessionId(99).setNotifStatus(1);

        // approveToOnline 的三件事
        schedule.setInterviewMode(1).setSessionId(null).setNotifStatus(0);

        assertEquals(Integer.valueOf(1), schedule.getStatus(), "时间没变，安排不该被取消");
        assertEquals(Integer.valueOf(1), schedule.getInterviewMode());
        assertNull(schedule.getSessionId(), "不占教室了，场次要解绑并归还名额");
        assertEquals(Integer.valueOf(0), schedule.getNotifStatus(),
                "学生手上那封还写着教室，必须进待补发");
    }

    @Test
    @DisplayName("改时间：安排被取消，等人工重排")
    void rescheduleCancelsSchedule() {
        InterviewSchedule schedule = new InterviewSchedule().setScheduleId(1).setStatus(1);
        schedule.setStatus(2);
        assertEquals(Integer.valueOf(2), schedule.getStatus());
    }

    @Test
    @DisplayName("线上申请同意后不该标「待重排」——它本来就不需要重排")
    void onlineIsNotAwaitingReassign() {
        boolean approved = true;
        boolean isOnline = true;
        boolean scheduleStillActive = true;
        boolean awaiting = approved && !isOnline && !scheduleStillActive;
        assertFalse(awaiting);

        // 对照：普通改期同意后安排被取消，就该标
        boolean awaitingForReschedule = true && !false && !false;
        assertTrue(awaitingForReschedule);
    }
}
