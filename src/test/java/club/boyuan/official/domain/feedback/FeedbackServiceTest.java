package club.boyuan.official.domain.feedback;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.domain.feedback.service.impl.FeedbackServiceImpl;
import club.boyuan.official.persistence.entity.Feedback;
import club.boyuan.official.persistence.mapper.FeedbackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 反馈的两处关键规则：截图的归属校验，和写入时的取值收敛。
 *
 * 截图那条是安全边界——反馈里常有带个人信息的截图，不能靠「猜不到 id」当防线。
 */
class FeedbackServiceTest {

    private FeedbackMapper mapper;
    private FeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FeedbackMapper.class);
        service = new FeedbackServiceImpl(mapper);
    }

    private static Feedback feedbackOf(Integer ownerId, List<String> keys) {
        Feedback f = new Feedback();
        f.setFeedbackId(1L);
        f.setUserId(ownerId);
        f.setImageKeys(keys);
        return f;
    }

    @Test
    @DisplayName("本人能取自己的截图")
    void ownerCanReadOwnImage() {
        when(mapper.selectById(1L)).thenReturn(feedbackOf(7, List.of("feedback/a.png")));
        assertEquals("feedback/a.png", service.imageKeyFor(1L, 0, 7, false));
    }

    @Test
    @DisplayName("别人取不到——反馈截图常带个人信息，不能只靠 id 难猜")
    void strangerCannotRead() {
        when(mapper.selectById(1L)).thenReturn(feedbackOf(7, List.of("feedback/a.png")));
        assertThrows(BusinessException.class, () -> service.imageKeyFor(1L, 0, 99, false));
    }

    @Test
    @DisplayName("有 feedback:view 的管理员能取")
    void adminCanRead() {
        when(mapper.selectById(1L)).thenReturn(feedbackOf(7, List.of("feedback/a.png")));
        assertEquals("feedback/a.png", service.imageKeyFor(1L, 0, 99, true));
    }

    @Test
    @DisplayName("序号越界与没有截图都按「不存在」处理，不能漏出空指针")
    void outOfRangeIsNotFound() {
        when(mapper.selectById(1L)).thenReturn(feedbackOf(7, List.of("feedback/a.png")));
        assertThrows(BusinessException.class, () -> service.imageKeyFor(1L, 5, 7, true));
        assertThrows(BusinessException.class, () -> service.imageKeyFor(1L, -1, 7, true));

        when(mapper.selectById(2L)).thenReturn(feedbackOf(7, null));
        assertThrows(BusinessException.class, () -> service.imageKeyFor(2L, 0, 7, true));
    }

    @Test
    @DisplayName("反馈不存在时抛业务异常，不是 NPE")
    void missingFeedback() {
        when(mapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.imageKeyFor(9L, 0, 7, true));
    }

    @Test
    @DisplayName("写入时收敛取值：分类兜底 other、内容去空白、截图最多 3 张、空列表存 null")
    void createNormalisesInput() {
        when(mapper.insert(any(Feedback.class))).thenReturn(1);

        Feedback a = service.create(7, null, "  页面报错了  ",
                List.of("k1", "k2", "k3", "k4"));
        assertEquals("other", a.getCategory());
        assertEquals("页面报错了", a.getContent());
        assertEquals(3, a.getImageKeys().size(), "超出上限的截图应被截断");

        // 空列表存 null：JSON_LENGTH 对空串会报错，统一成一种空值
        Feedback b = service.create(7, "bug", "x", List.of());
        assertNull(b.getImageKeys());
        assertEquals("bug", b.getCategory());
    }
}
