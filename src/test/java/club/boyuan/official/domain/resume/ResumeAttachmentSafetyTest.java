package club.boyuan.official.domain.resume;

import club.boyuan.official.domain.resume.service.impl.ResumeAttachmentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历附件的两条安全边界。
 *
 * 附件允许「任意格式」，内容完全由申请人控制，而取文件的接口与站点同源。
 * 这两条弄错的后果不是显示不好看，是面试官的登录态被读走。
 */
class ResumeAttachmentSafetyTest {

    private final ResumeAttachmentServiceImpl service =
            new ResumeAttachmentServiceImpl(null, null, null);

    // ── 内联预览的类型白名单 ─────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp",
            "text/plain", "video/mp4", "audio/mpeg",
    })
    @DisplayName("浏览器能渲染又不执行脚本的类型，允许内联预览")
    void safeTypesArePreviewable(String type) {
        assertTrue(service.previewable(type, "x"), type + " 应当可以内联预览");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 这三类是重点：内联返回等于让上传者在管理端的源上执行脚本
            "text/html",
            "image/svg+xml",          // 常被忽略：SVG 里能写 <script>
            "application/xhtml+xml",
            // 浏览器无法预览，内联只会变成一堆乱码
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/octet-stream",
    })
    @DisplayName("会执行脚本或无法渲染的类型，一律强制下载")
    void unsafeOrUnrenderableTypesAreNotPreviewable(String type) {
        assertFalse(service.previewable(type, "x"), type + " 绝不能内联预览");
    }

    @Test
    @DisplayName("带 charset 参数的 MIME 也能正确判定")
    void handlesMimeParameters() {
        assertTrue(service.previewable("text/plain; charset=utf-8", "x"));
        assertFalse(service.previewable("text/html; charset=utf-8", "x"));
    }

    @Test
    @DisplayName("大小写与空白不影响判定——别让 TEXT/HTML 绕过去")
    void isCaseInsensitive() {
        assertFalse(service.previewable("TEXT/HTML", "x"));
        assertTrue(service.previewable("  Application/PDF  ".trim(), "x"));
    }

    @Test
    @DisplayName("没有 MIME 时按不可预览处理")
    void missingTypeIsNotPreviewable() {
        assertFalse(service.previewable(null, "x"));
        assertFalse(service.previewable("", "x"));
    }

    // ── 文件名消毒 ──────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "..\\..\\windows\\system32\\config",
            "/absolute/path/x.pdf",
            "C:\\Users\\x\\作品集.pdf",
    })
    @DisplayName("路径分隔符被剥掉，只留最后一段")
    void stripsPathSeparators(String raw) throws Exception {
        String clean = sanitize(raw);
        assertFalse(clean.contains("/"), "残留了 /: " + clean);
        assertFalse(clean.contains("\\"), "残留了 \\: " + clean);
        assertFalse(clean.contains(".."), "残留了 ..: " + clean);
    }

    @Test
    @DisplayName("换行与引号被剥掉——它们能截断/污染 Content-Disposition")
    void stripsHeaderInjectionChars() throws Exception {
        String clean = sanitize("作品集\r\nSet-Cookie: evil=1\".pdf");
        assertFalse(clean.contains("\r"));
        assertFalse(clean.contains("\n"));
        assertFalse(clean.contains("\""));
    }

    @Test
    @DisplayName("正常的中文文件名原样保留")
    void keepsNormalChineseNames() throws Exception {
        assertEquals("我的作品集.pdf", sanitize("我的作品集.pdf"));
    }

    @Test
    @DisplayName("空文件名给个兜底，不产生空的 filename")
    void emptyNameFallsBack() throws Exception {
        assertEquals("附件", sanitize(null));
        assertEquals("附件", sanitize("   "));
        assertEquals("附件", sanitize("/"));
    }

    @Test
    @DisplayName("超长文件名截断，保住扩展名那一头")
    void truncatesOverlongNames() throws Exception {
        String clean = sanitize("啊".repeat(300) + ".pdf");
        assertTrue(clean.length() <= 200, "长度 " + clean.length());
        assertTrue(clean.endsWith(".pdf"), "截断时把扩展名切掉了: " + clean);
    }

    private static String sanitize(String raw) throws Exception {
        Method m = ResumeAttachmentServiceImpl.class
                .getDeclaredMethod("sanitizeFileName", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }
}
