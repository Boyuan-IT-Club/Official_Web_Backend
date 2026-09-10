package club.boyuan.official.domain.user.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.MemberClaim;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.MemberClaimMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 老社员认领：提交侧的三道闸门与审批侧的落库效果。
 *
 * 重点防两件事：已是社员的人重复申请（多半是误点），
 * 以及两个管理员同时审批把结论覆盖成相反的。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberClaimServiceTest {

    @Mock private MemberClaimMapper claimMapper;
    @Mock private UserMapper userMapper;
    @Mock private DepartmentMapper departmentMapper;

    @InjectMocks private MemberClaimServiceImpl service;

    private static final int USER_ID = 7;
    private User user;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemberClaim.class);
        TableInfoHelper.initTableInfo(assistant, User.class);

        user = new User();
        user.setUserId(USER_ID);
        user.setUsername("10245101480");
        user.setIsMember(false);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(claimMapper.selectCount(any())).thenReturn(0L);
        when(claimMapper.insert(any(MemberClaim.class))).thenReturn(1);
        when(departmentMapper.selectById(any())).thenReturn(new Department());
    }

    private static MemberClaim form() {
        return new MemberClaim().setRealName("丁华烨").setJoinYear("2024").setDeptId(1)
                .setEvidence("2024 级技术部，做过官网后端");
    }

    @Test
    @DisplayName("正常提交：状态为待审核，姓名去空格")
    void submitCreatesPendingClaim() {
        MemberClaim c = service.submit(USER_ID, form().setRealName("  丁华烨  "));
        assertEquals(MemberClaim.STATUS_PENDING, c.getStatus());
        assertEquals("丁华烨", c.getRealName());
        assertEquals(USER_ID, c.getUserId());
    }

    @Test
    @DisplayName("已经是社员：拒绝重复认领")
    void memberCannotClaimAgain() {
        user.setIsMember(true);
        BusinessException e = assertThrows(BusinessException.class, () -> service.submit(USER_ID, form()));
        assertTrue(e.getMessage().contains("已经是社员"));
        verify(claimMapper, never()).insert(any(MemberClaim.class));
    }

    @Test
    @DisplayName("已有待审申请：不允许再交一份")
    void pendingClaimBlocksResubmit() {
        when(claimMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.submit(USER_ID, form()));
        verify(claimMapper, never()).insert(any(MemberClaim.class));
    }

    @Test
    @DisplayName("没填姓名：直接拒绝（名册核对全靠它）")
    void nameIsRequired() {
        assertThrows(BusinessException.class, () -> service.submit(USER_ID, form().setRealName("  ")));
    }

    @Test
    @DisplayName("通过：置为社员并写部门，只动这两列")
    void approveMarksMemberAndDept() {
        MemberClaim pending = new MemberClaim().setClaimId(1).setUserId(USER_ID)
                .setStatus(MemberClaim.STATUS_PENDING).setDeptId(1);
        when(claimMapper.selectById(1)).thenReturn(pending);

        List<String> sets = new ArrayList<>();
        when(userMapper.update(any(), any())).thenAnswer(inv -> {
            LambdaUpdateWrapper<?> w = inv.getArgument(1);
            sets.add(w.getSqlSet());
            return 1;
        });

        MemberClaim done = service.approve(1, 99, 2, "名册核对无误");
        assertEquals(MemberClaim.STATUS_APPROVED, done.getStatus());
        assertEquals(2, done.getDeptId(), "审批时传的部门应覆盖申请里填的");
        assertEquals(99, done.getReviewedBy());
        assertNotNull(done.getReviewedAt());
        // 整实体写回会把审批窗口里用户自己改的资料按旧值盖掉，所以必须是定点更新
        assertEquals(1, sets.size());
        assertTrue(sets.get(0).contains("is_member"));
        assertTrue(sets.get(0).contains("dept_id"));
        assertFalse(sets.get(0).contains("avatar"), "不该顺手改其它列");
    }

    @Test
    @DisplayName("重复审批：第二个管理员会被挡下，结论不被覆盖")
    void doubleReviewIsRejected() {
        MemberClaim approved = new MemberClaim().setClaimId(1).setUserId(USER_ID)
                .setStatus(MemberClaim.STATUS_APPROVED);
        when(claimMapper.selectById(1)).thenReturn(approved);

        assertThrows(BusinessException.class, () -> service.reject(1, 100, "我觉得不行"));
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("驳回：记下理由，账号仍不是社员")
    void rejectKeepsNonMember() {
        MemberClaim pending = new MemberClaim().setClaimId(2).setUserId(USER_ID)
                .setStatus(MemberClaim.STATUS_PENDING);
        when(claimMapper.selectById(2)).thenReturn(pending);

        MemberClaim done = service.reject(2, 99, "名册里查无此人，请补充材料");
        assertEquals(MemberClaim.STATUS_REJECTED, done.getStatus());
        assertTrue(done.getReviewNote().contains("查无此人"));
        verify(userMapper, never()).update(any(), any());
    }
}
