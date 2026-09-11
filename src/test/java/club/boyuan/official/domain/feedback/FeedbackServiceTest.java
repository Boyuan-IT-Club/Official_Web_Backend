package club.boyuan.official.domain.feedback;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.domain.feedback.service.impl.FeedbackServiceImpl;
import club.boyuan.official.persistence.entity.Feedback;
import club.boyuan.official.persistence.mapper.FeedbackMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
        // LambdaUpdateWrapper 要查表信息缓存，纯单测里没有 Spring 容器帮忙初始化，
        // 不初始化会报 "can not find lambda cache for this entity"
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Feedback.class);

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
    @DisplayName("标为已处理记下标记人与时间；取消时一并清空，不留残影")
    void markHandledRecordsAndClears() {
        when(mapper.selectById(1L)).thenReturn(feedbackOf(7, null));

        service.markHandled(1L, true, 42);
        org.mockito.ArgumentCaptor<Feedback> captor =
                org.mockito.ArgumentCaptor.forClass(Feedback.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getHandled());
        assertEquals(42, captor.getValue().getHandledBy());
        assertNotNull(captor.getValue().getHandledAt());

        // 取消要走 UpdateWrapper：updateById 跳过 null，标记人会留在库里
        service.markHandled(1L, false, 42);
        verify(mapper).update(isNull(), any());
    }

    @Test
    @DisplayName("标记不存在的反馈抛业务异常")
    void markMissingFeedback() {
        when(mapper.selectById(8L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markHandled(8L, true, 1));
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
