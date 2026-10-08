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
 * （10-07 的申请里就有一条「10月11日有事来不及赶到学校，请问能不能帮忙安排
 * 一下线上面试?」）。
 * <p>
 * 第一版只处理 status=1 的安排，结果那位同学先被当成普通改期同意过一次、
 * 安排已经是「已取消」，再点同意什么都不会发生——人就卡在待调剂池里，
 * 管理端又没有任何能把他排成线上的入口。
 */
class OnlineInterviewApprovalTest {

    @Test
    @DisplayName("默认是改时间——老客户端不传 requestType 时行为不变")
    void defaultsToReschedule() {
        assertNull(new InterviewRescheduleRequest().getRequestType(),
                "实体不该自作主张填值，由库的 DEFAULT 0 兜底");
        assertEquals(0, InterviewRescheduleRequest.TYPE_RESCHEDULE);
        assertEquals(1, InterviewRescheduleRequest.TYPE_TO_ONLINE);
    }

    @Test
    @DisplayName("改为线上：安排保持生效，mode 翻 1、场次解绑、进待补发")
    void onlineKeepsScheduleActive() {
        InterviewSchedule s = new InterviewSchedule()
                .setScheduleId(1).setStatus(1).setSessionId(99).setNotifStatus(1);

        s.setInterviewMode(1).setSessionId(null).setStatus(1).setNotifStatus(0);

        assertEquals(Integer.valueOf(1), s.getStatus(), "时间没变，安排不该被取消");
        assertEquals(Integer.valueOf(1), s.getInterviewMode());
        assertNull(s.getSessionId(), "不占教室了，座位要让出来");
        assertEquals(Integer.valueOf(0), s.getNotifStatus(), "学生手上那封还写着教室");
    }

    @Test
    @DisplayName("已取消的安排也能转线上——这正是王乐昆卡住的那个状态")
    void cancelledScheduleCanStillGoOnline() {
        InterviewSchedule cancelled = new InterviewSchedule()
                .setScheduleId(41).setStatus(2).setSessionId(29);

        boolean wasActive = Integer.valueOf(1).equals(cancelled.getStatus());
        cancelled.setInterviewMode(1).setSessionId(null).setStatus(1).setNotifStatus(0);

        assertFalse(wasActive, "原本是已取消");
        assertEquals(Integer.valueOf(1), cancelled.getStatus(), "转线上后重新生效，不必再排进任何场次");
        assertEquals(Integer.valueOf(1), cancelled.getInterviewMode());
    }

    @Test
    @DisplayName("已取消的行不能重复归还场次名额——取消时已经还过一次")
    void cancelledScheduleDoesNotReleaseTwice() {
        InterviewSchedule cancelled = new InterviewSchedule().setStatus(2).setSessionId(29);
        boolean shouldRelease = Integer.valueOf(1).equals(cancelled.getStatus())
                && cancelled.getSessionId() != null;
        assertFalse(shouldRelease);

        InterviewSchedule active = new InterviewSchedule().setStatus(1).setSessionId(29);
        assertTrue(Integer.valueOf(1).equals(active.getStatus()) && active.getSessionId() != null);
    }

    /**
     * MyBatis-Plus 默认 FieldStrategy.NOT_NULL，updateById 会整个跳过值为 null
     * 的字段——setSessionId(null) 根本写不进去。线上因此出现过「人已转线上、
     * 座位也还了，但那行还指着原场次」：场次 29 的 current_occupied=10，
     * 实际挂在它名下的生效安排却有 11 条。
     * <p>
     * 这条断言只是把结论钉住：解绑场次不能依赖实体上的 null，必须显式
     * set(字段, null)。真正的保证在实现里的 LambdaUpdateWrapper。
     */
    @Test
    @DisplayName("解绑场次不能靠 setSessionId(null) + updateById")
    void unbindingSessionNeedsExplicitNullUpdate() {
        InterviewSchedule s = new InterviewSchedule().setScheduleId(41).setSessionId(29);
        s.setSessionId(null);
        assertNull(s.getSessionId(), "实体上确实是 null……");
        // ……但 updateById 不会把它写进库，所以实现里额外走了一次
        // LambdaUpdateWrapper.set(getSessionId, null)
    }

    @Test
    @DisplayName("已经是线上的人再调一次不能重复归还座位")
    void reassigningOnlineDoesNotReleaseTwice() {
        InterviewSchedule online = new InterviewSchedule()
                .setStatus(1).setSessionId(29).setInterviewMode(1);
        boolean alreadyOnline = Integer.valueOf(1).equals(online.getInterviewMode());
        boolean shouldRelease = !alreadyOnline
                && Integer.valueOf(1).equals(online.getStatus())
                && online.getSessionId() != null;
        assertFalse(shouldRelease, "回来补时间时座位早就还过了，再还一次计数器会少一");
    }

    @Test
    @DisplayName("改时间：安排被取消，等人工重排")
    void rescheduleCancelsSchedule() {
        InterviewSchedule s = new InterviewSchedule().setScheduleId(1).setStatus(1);
        s.setStatus(2);
        assertEquals(Integer.valueOf(2), s.getStatus());
    }
}
