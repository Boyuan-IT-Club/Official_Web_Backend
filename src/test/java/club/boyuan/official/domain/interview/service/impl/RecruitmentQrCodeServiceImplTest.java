package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import club.boyuan.official.persistence.mapper.RecruitmentQrCodeMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 社团大群满员后开了第二个群：录取者两张大群码都要收到，且第二个群的码要能上传。
 */
class RecruitmentQrCodeServiceImplTest {

    private final RecruitmentQrCodeMapper mapper = mock(RecruitmentQrCodeMapper.class);
    private final CosStorageService cos = mock(CosStorageService.class);
    private final RecruitmentQrCodeServiceImpl service = new RecruitmentQrCodeServiceImpl(mapper, cos);

    private static RecruitmentQrCode qr(String type, int deptId, String key) {
        return new RecruitmentQrCode().setCycleId(14).setQrType(type).setDeptId(deptId).setImageUrl(key);
    }

    @Test
    @DisplayName("录取者收到：本部门群、社团大群、社团大群 2，按这个顺序")
    @SuppressWarnings("unchecked")
    void admittedGetBothMainGroups() {
        // forAdmitted 依次查：部门群、大群、大群 2
        Deque<RecruitmentQrCode> answers = new ArrayDeque<>(List.of(
                qr(RecruitmentQrCode.TYPE_DEPT, 1, "dept-1"),
                qr(RecruitmentQrCode.TYPE_MAIN_GROUP, 0, "main-1"),
                qr(RecruitmentQrCode.TYPE_MAIN_GROUP_2, 0, "main-2")));
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> answers.poll());
        when(cos.resolveAvatarUrl(anyString())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));

        List<RecruitmentQrCode> out = service.forAdmitted(14, 1);

        assertEquals(List.of("https://cdn/dept-1", "https://cdn/main-1", "https://cdn/main-2"),
                out.stream().map(RecruitmentQrCode::getImageUrl).toList());
    }

    @Test
    @DisplayName("没配第二个大群时照旧只有部门群 + 大群")
    @SuppressWarnings("unchecked")
    void secondMainGroupIsOptional() {
        Deque<RecruitmentQrCode> answers = new ArrayDeque<>();
        answers.add(qr(RecruitmentQrCode.TYPE_DEPT, 1, "dept-1"));
        answers.add(qr(RecruitmentQrCode.TYPE_MAIN_GROUP, 0, "main-1"));
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> answers.poll());
        when(cos.resolveAvatarUrl(anyString())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(List.of("dept-1", "main-1"),
                service.forAdmitted(14, 1).stream().map(RecruitmentQrCode::getImageUrl).toList());
    }

    @Test
    @DisplayName("第二个大群的码能上传，且按「与部门无关」存（dept_id=0），不会被当成部门群")
    void mainGroup2CanBeUploaded() throws Exception {
        when(cos.upload(any(), eq("qrcodes"))).thenReturn("qrcodes/main2.png");
        when(cos.resolveAvatarUrl(anyString())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "main2.png", "image/png", new byte[]{1, 2, 3});

        RecruitmentQrCode saved = service.upload(14, RecruitmentQrCode.TYPE_MAIN_GROUP_2, 3, "社团大群 2", file);

        assertEquals(RecruitmentQrCode.TYPE_MAIN_GROUP_2, saved.getQrType());
        assertEquals(RecruitmentQrCode.NO_DEPT, saved.getDeptId());
        verify(mapper).insert(any(RecruitmentQrCode.class));
    }

    @Test
    @DisplayName("未知类型仍然拒收")
    void unknownTypeStillRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        assertThrows(BusinessException.class, () -> service.upload(14, "MAIN_GROUP_3", null, null, file));
    }
}
