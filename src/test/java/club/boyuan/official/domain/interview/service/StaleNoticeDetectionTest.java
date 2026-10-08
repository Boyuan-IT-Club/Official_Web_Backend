package club.boyuan.official.domain.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「他收到的是旧安排」的判定。
 * <p>
 * 这个判据改过一次，两次的教训都值得留着：
 * <ol>
 *   <li>2026-10-07：五位同学改期重排后，notif_status 仍是 1，通知中心显示
 *       「已发 97/97、未发 0」，待办里一个都看不到，人一直拿着十小时前的旧时间。
 *       结论是任何基于标记位的口径都会漏。</li>
 *   <li>于是第一版改用「通知发送时间 &lt; 安排 updated_at」。线上一跑把
 *       <b>97 个人全标成需补发</b>——updated_at 是「这行被写过」，不是「学生
 *       该知道的信息变了」。误报的代价是群发一百封重复邮件，比漏报严重得多。</li>
 * </ol>
 * 现在比的是事实本身：发信时把正文里的面试时间记下来（V52），之后直接对比。
 */
class StaleNoticeDetectionTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 11, 9, 0);
    private static final LocalDateTime EIGHT_PM = LocalDateTime.of(2026, 10, 11, 20, 0);

    @Test
    @DisplayName("通知里写的时间 ≠ 现在的时间 → 过期，必须补发")
    void changedTimeIsStale() {
        assertTrue(NotificationCenterService.isNoticeStale(NINE, EIGHT_PM),
                "告诉他 9 点，现在排在 20 点——他手上那封是废的");
    }

    @Test
    @DisplayName("时间没变 → 不过期，哪怕这行被写过很多次")
    void unchangedTimeIsNotStale() {
        assertFalse(NotificationCenterService.isNoticeStale(NINE, NINE),
                "这正是第一版栽的地方：批量重算把 97 行的 updated_at 全顶上去，"
                        + "但没有一个人的面试时间变了");
    }

    @Test
    @DisplayName("历史数据（V52 之前没记）一律不判过期——宁可漏报也不能群发")
    void legacyRowsAreNeverStale() {
        assertFalse(NotificationCenterService.isNoticeStale(null, NINE));
    }

    @Test
    @DisplayName("安排时间为空时不下结论")
    void missingCurrentTimeIsNotStale() {
        assertFalse(NotificationCenterService.isNoticeStale(NINE, null));
    }

    @Test
    @DisplayName("差一分钟也算变了——不设容差，因为比的是事实不是时序")
    void anyDifferenceCounts() {
        assertTrue(NotificationCenterService.isNoticeStale(NINE, NINE.plusMinutes(1)));
    }
}
