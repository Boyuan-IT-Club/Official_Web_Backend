package club.boyuan.official.domain.resume;

import club.boyuan.official.domain.resume.service.impl.ResumeAttachmentServiceImpl;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.ResumeAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 附件直链的签发。直链绕开了 /content，所以白名单那道闸得在签名这里重新守一遍：
 * 只有服务端认定安全的类型才能以 inline 下发，其余一律 attachment。
 */
class AttachmentPresignTest {

    private CosStorageService cos;
    private ResumeAttachmentServiceImpl service;

    @BeforeEach
    void setUp() {
        cos = mock(CosStorageService.class);
        when(cos.isEnabled()).thenReturn(true);
        when(cos.presignGet(anyString(), any(), any(), any())).thenReturn("https://signed");
        service = new ResumeAttachmentServiceImpl(null, cos, null);
    }

    private static ResumeAttachment att(String name, String type) {
        ResumeAttachment a = new ResumeAttachment();
        a.setObjectKey("resume-attachments/x");
        a.setFileName(name);
        a.setContentType(type);
        return a;
    }

    private String[] captured() {
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> disp = ArgumentCaptor.forClass(String.class);
        verify(cos).presignGet(eq("resume-attachments/x"), any(Duration.class), type.capture(), disp.capture());
        return new String[] {type.getValue(), disp.getValue()};
    }

    @Test
    @DisplayName("PDF 预览：inline，类型钉死为 application/pdf")
    void pdfInline() {
        service.presignedUrl(att("作品集.pdf", "application/pdf"), true);
        String[] c = captured();
        assertEquals("application/pdf", c[0]);
        assertTrue(c[1].startsWith("inline;"), c[1]);
    }

    @Test
    @DisplayName("请求预览但类型不在白名单（html）：降级成 attachment —— 不能借直链把网页内联出来")
    void htmlNeverInline() {
        service.presignedUrl(att("x.html", "text/html"), true);
        assertTrue(captured()[1].startsWith("attachment;"));
    }

    @Test
    @DisplayName("svg 同样不内联：它能带脚本")
    void svgNeverInline() {
        service.presignedUrl(att("x.svg", "image/svg+xml"), true);
        assertTrue(captured()[1].startsWith("attachment;"));
    }

    @Test
    @DisplayName("下载（inline=false）：安全类型也是 attachment")
    void downloadIsAttachment() {
        service.presignedUrl(att("作品集.pdf", "application/pdf"), false);
        assertTrue(captured()[1].startsWith("attachment;"));
    }

    @Test
    @DisplayName("中文文件名按 RFC 5987 编码，空格是 %20 不是 +")
    void filenameEncoded() {
        service.presignedUrl(att("我的 作品集.pdf", "application/pdf"), true);
        String disp = captured()[1];
        assertTrue(disp.contains("filename*=UTF-8''"), disp);
        assertTrue(disp.contains("%20"), disp);
        assertTrue(!disp.contains("+"), disp);
    }

    @Test
    @DisplayName("内联类型去掉 charset 等参数、统一小写，只用白名单里的那个值")
    void inlineTypeNormalized() {
        service.presignedUrl(att("a.txt", "Text/Plain; charset=UTF-8"), true);
        assertEquals("text/plain", captured()[0]);
    }

    @Test
    @DisplayName("COS 未启用：返回 null，不去签名")
    void nullWhenCosDisabled() {
        when(cos.isEnabled()).thenReturn(false);
        assertNull(service.presignedUrl(att("a.pdf", "application/pdf"), true));
        verify(cos, never()).presignGet(anyString(), any(), any(), any());
    }
}
