package club.boyuan.official.domain.activity;

import club.boyuan.official.domain.activity.service.impl.ActivityServiceImpl;
import club.boyuan.official.persistence.entity.Activity;
import club.boyuan.official.persistence.mapper.ActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 活动图文详情的消毒守卫：富文本编辑器的产出不可信，
 * 绕过前端直接调接口也只能把白名单内的标签写进库。
 */
class ActivityDetailSanitizeTest {

    private ActivityMapper activityMapper;
    private ActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        activityMapper = Mockito.mock(ActivityMapper.class);
        service = new ActivityServiceImpl(activityMapper);
    }

    private Activity insertedActivity(String detailContent) {
        Activity activity = new Activity();
        activity.setTitle("周年展");
        activity.setDetailContent(detailContent);
        when(activityMapper.insert(any(Activity.class))).thenReturn(1);
        service.createActivity(activity);
        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        Mockito.verify(activityMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("脚本与事件属性被剥掉，常规排版标签保留")
    void stripsScriptButKeepsFormatting() {
        Activity saved = insertedActivity(
                "<h2>回顾</h2><p class=\"ql-align-center\">精彩<strong>瞬间</strong></p>"
                        + "<script>alert(1)</script><img src=\"x\" onerror=\"alert(2)\">");
        assertThat(saved.getDetailContent()).contains("<h2>回顾</h2>");
        assertThat(saved.getDetailContent()).contains("<strong>瞬间</strong>");
        // Quill 的对齐靠 class，白名单专门放行了它
        assertThat(saved.getDetailContent()).contains("ql-align-center");
        assertThat(saved.getDetailContent()).doesNotContain("<script>");
        assertThat(saved.getDetailContent()).doesNotContain("onerror");
    }

    @Test
    @DisplayName("图片只允许 http/https 地址，data: 内嵌与脚本协议被拦")
    void allowsOnlyHttpImages() {
        Activity saved = insertedActivity(
                "<img src=\"https://cos.example.com/activities/a.png\">"
                        + "<img src=\"data:text/html;base64,PHNjcmlwdD4=\">"
                        + "<a href=\"javascript:alert(1)\">点我</a>");
        assertThat(saved.getDetailContent()).contains("https://cos.example.com/activities/a.png");
        assertThat(saved.getDetailContent()).doesNotContain("data:");
        assertThat(saved.getDetailContent()).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("后端中转的站内活动图片地址会被保留")
    void keepsBackendProxyImageUrl() {
        Activity saved = insertedActivity(
                "<p>现场照片</p>"
                        + "<img src=\"/api/files/activities/a.png\">"
                        + "<img src=\"/uploads/activities/b.webp\">"
                        + "<img src=\"/api/files/activities/../private/x.png\">"
                        + "<img src=\"//evil.example/x.png\">");

        assertThat(saved.getDetailContent())
                .contains("src=\"/api/files/activities/a.png\"")
                .contains("src=\"/uploads/activities/b.webp\"")
                .doesNotContain("../private")
                .doesNotContain("evil.example");
    }

    @Test
    @DisplayName("详情为 null 时原样保留，不会被消毒成空串")
    void keepsNullDetail() {
        Activity saved = insertedActivity(null);
        assertThat(saved.getDetailContent()).isNull();
    }
}
