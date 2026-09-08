package club.boyuan.official.domain.resume;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.service.impl.ResumePhotoServiceImpl;
import club.boyuan.official.infra.storage.CosFile;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.persistence.mapper.ResumeFieldDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 简历照片：上传的准入闸门与读取侧的引用校验。
 *
 * 读取侧那条前缀校验是安全边界：personal_photo 字段值是学生可写的普通字符串，
 * 若照片接口肯读任意 key，它就成了整个 COS 桶的越权读取通道。
 */
class ResumePhotoServiceTest {

    private CosStorageService cos;
    private club.boyuan.official.domain.resume.service.IResumeService resumeService;
    private ResumeFieldDefinitionMapper defMapper;
    private ResumePhotoServiceImpl service;

    private static final Integer RESUME_ID = 7;
    private static final Integer OWNER_ID = 42;
    private static final Integer CYCLE_ID = 3;

    @BeforeEach
    void setUp() {
        cos = mock(CosStorageService.class);
        resumeService = mock(club.boyuan.official.domain.resume.service.IResumeService.class);
        defMapper = mock(ResumeFieldDefinitionMapper.class);
        service = new ResumePhotoServiceImpl(cos, resumeService, defMapper);

        Resume resume = new Resume();
        resume.setResumeId(RESUME_ID);
        resume.setUserId(OWNER_ID);
        resume.setCycleId(CYCLE_ID);
        when(resumeService.getResumeById(RESUME_ID)).thenReturn(resume);
    }

    // ── 上传闸门 ────────────────────────────────────────

    @Test
    @DisplayName("正常上传：进 resume-photos/ 前缀，返回 objectKey")
    void uploadReturnsObjectKey() throws Exception {
        when(cos.upload(any(), anyString())).thenReturn("resume-photos/u.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1, 2});

        String key = service.upload(RESUME_ID, OWNER_ID, file);

        assertEquals("resume-photos/u.jpg", key);
        verify(resumeService).assertCycleOpen(CYCLE_ID);
        verify(cos).upload(eq(file), eq("resume-photos/"));
    }

    @Test
    @DisplayName("只能传自己的简历")
    void uploadRejectsOthersResume() {
        MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1});
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upload(RESUME_ID, OWNER_ID + 1, file));
        assertEquals(BusinessExceptionEnum.PERMISSION_DENIED.getCode(), e.getCode());
    }

    @Test
    @DisplayName("超过 5MB 拒收")
    void uploadRejectsOversize() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]);
        assertThrows(BusinessException.class, () -> service.upload(RESUME_ID, OWNER_ID, file));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // svg 能带 <script>，照片场景没有任何理由收它
            "image/svg+xml",
            "text/html",
            "application/pdf",
            "application/octet-stream",
    })
    @DisplayName("非位图类型一律拒收")
    void uploadRejectsNonBitmapTypes(String type) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x", type, new byte[]{1});
        assertThrows(BusinessException.class, () -> service.upload(RESUME_ID, OWNER_ID, file));
        verify(cos, never()).upload(any(), anyString());
        verify(cos, never()).upload(any(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("空文件拒收")
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);
        assertThrows(BusinessException.class, () -> service.upload(RESUME_ID, OWNER_ID, file));
    }

    // ── 读取：兼容两种字段值形态 ─────────────────────────

    @Test
    @DisplayName("字段值是 objectKey：从 COS 拉流")
    void openStreamsFromCosForObjectKey() {
        stubPhotoFieldValue("resume-photos/u.jpg");
        CosFile expected = new CosFile(new ByteArrayInputStream(new byte[]{9}), "image/jpeg", 1);
        when(cos.open("resume-photos/u.jpg")).thenReturn(expected);

        assertEquals(expected, service.openByResumeId(RESUME_ID));
    }

    @Test
    @DisplayName("字段值是历史 base64：就地解码，类型与长度正确")
    void openDecodesLegacyBase64() throws Exception {
        byte[] raw = {(byte) 0xFF, (byte) 0xD8, 0x00, 0x11};
        stubPhotoFieldValue("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(raw));

        CosFile file = service.openByResumeId(RESUME_ID);

        assertEquals("image/jpeg", file.contentType());
        assertEquals(raw.length, file.contentLength());
        assertArrayEquals(raw, file.inputStream().readAllBytes());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 越权探针：别的前缀的 key，读了就等于把桶交出去
            "attachments/deadbeef.pdf",
            "avatars/x.png",
            // 脏数据
            "not-a-key",
            "data:garbage",
    })
    @DisplayName("非 resume-photos/ 前缀的引用一律按无照片处理，绝不回源 COS")
    void openRejectsForeignReferences(String fieldValue) {
        stubPhotoFieldValue(fieldValue);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.openByResumeId(RESUME_ID));
        assertEquals(BusinessExceptionEnum.RESUME_PHOTO_NOT_FOUND.getCode(), e.getCode());
        verify(cos, never()).open(anyString());
    }

    @Test
    @DisplayName("没填照片按 404 处理")
    void openThrowsWhenNoPhoto() {
        stubPhotoFieldValue(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.openByResumeId(RESUME_ID));
        assertEquals(BusinessExceptionEnum.RESUME_PHOTO_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("简历不存在按 404 处理")
    void openThrowsWhenResumeMissing() {
        when(resumeService.getResumeById(999)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.openByResumeId(999));
        assertEquals(BusinessExceptionEnum.RESUME_NOT_FOUND.getCode(), e.getCode());
    }

    // ── PDF 导出取字节 ──────────────────────────────────

    @Test
    @DisplayName("objectKey 取回字节")
    void readBytesForObjectKey() {
        when(cos.download("resume-photos/u.jpg"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertArrayEquals(new byte[]{1, 2, 3}, service.readBytesIfObjectKey("resume-photos/u.jpg"));
    }

    @Test
    @DisplayName("历史 base64 返回 null，由导出工具按旧逻辑解析")
    void readBytesReturnsNullForBase64() {
        assertNull(service.readBytesIfObjectKey("data:image/jpeg;base64,xxxx"));
        assertNull(service.readBytesIfObjectKey(null));
        assertNull(service.readBytesIfObjectKey(""));
    }

    @Test
    @DisplayName("COS 读取失败降级为 null，导出不因照片整体失败")
    void readBytesSwallowsCosFailure() {
        when(cos.download(anyString())).thenThrow(new RuntimeException("cos down"));
        assertNull(service.readBytesIfObjectKey("resume-photos/u.jpg"));
    }

    // ── helpers ─────────────────────────────────────────

    /** 让 personal_photo 字段定义存在、其值为给定内容 */
    private void stubPhotoFieldValue(String value) {
        ResumeFieldDefinition def = new ResumeFieldDefinition();
        def.setFieldId(11);
        def.setCycleId(CYCLE_ID);
        Mockito.when(defMapper.selectList(any())).thenReturn(List.of(def));

        ResumeFieldValue fv = new ResumeFieldValue();
        fv.setResumeId(RESUME_ID);
        fv.setFieldId(11);
        fv.setFieldValue(value);
        when(resumeService.getFieldValuesByResumeId(RESUME_ID)).thenReturn(List.of(fv));
    }
}
