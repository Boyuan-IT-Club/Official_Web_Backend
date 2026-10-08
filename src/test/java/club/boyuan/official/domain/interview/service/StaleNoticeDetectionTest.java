package club.boyuan.official.domain.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「他收到的是旧安排」的判定。
 * <p>
 * 2026-10-07 线上：五位同学改期重排后，通知发在 12:25、安排改在 22:49，
 * 而 notif_status 仍是 1——通知中心显示「已发」，待办里看不到他们，
 * 五个人一直拿着十小时前的旧时间，面试就在两天后。
 * <p>
 * 教训是：任何基于标记位的口径都会漏。标记位会被别的流程改、会忘了重置、
 * 会因为历史数据停在错的值上。时间戳不会——「通知发出去之后安排又变了」
 * 这件事本身就是事实，不需要谁去维护一个标记。
 */
class StaleNoticeDetectionTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 7, 12, 25);

    @Test
    @DisplayName("发完之后安排又被改过 → 过期，必须补发")
    void changedAfterNoticeIsStale() {
        assertTrue(NotificationCenterService.isNoticeStale(T, T.plusHours(10)),
                "线上那五位正是这种：通知 12:25，安排 22:49");
    }

    @Test
    @DisplayName("通知发在安排改动之后 → 不过期，他拿的就是最新的")
    void noticeAfterChangeIsFresh() {
        assertFalse(NotificationCenterService.isNoticeStale(T.plusHours(1), T));
    }

    @Test
    @DisplayName("从没发过 → 不算过期，那是「未发」，归另一个桶")
    void neverSentIsNotStale() {
        assertFalse(NotificationCenterService.isNoticeStale(null, T));
    }

    @Test
    @DisplayName("安排没有改动时间 → 不下结论")
    void missingUpdatedAtIsNotStale() {
        assertFalse(NotificationCenterService.isNoticeStale(T, null));
    }

    @Test
    @DisplayName("60 秒容差：发通知本身会带动 updated_at，不能让每个人发完就被判过期")
    void toleratesTheWriteCausedBySendingItself() {
        assertFalse(NotificationCenterService.isNoticeStale(T, T.plusSeconds(3)),
                "发送流程把 notif_status 置 1，updated_at 随之略晚于 sent_at，这不是改动");
        assertFalse(NotificationCenterService.isNoticeStale(T, T.plusSeconds(59)));
        assertTrue(NotificationCenterService.isNoticeStale(T, T.plusSeconds(61)),
                "超过容差就是真的改过了");
    }
}
