package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.domain.interview.dto.MaterializeEvaluationRequestDTO;
import club.boyuan.official.domain.interview.dto.SaveEvaluationDimensionsRequestDTO;
import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.domain.interview.service.IEvaluationDimensionService;
import club.boyuan.official.domain.interview.service.IInterviewEvaluationService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 评价相关接口的权限边界。
 * <p>
 * 这里用最小 Spring 上下文只装配控制器与方法级鉴权，不启动整个应用——
 * 本项目的 {@code @SpringBootTest} 控制器测试需要真实 MySQL 才能跑，
 * 而权限注解是否生效与数据库无关，没必要把这条断言绑在数据库上。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EvaluationApiSecurityTest.TestConfig.class)
class EvaluationApiSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        IEvaluationBoardService evaluationBoardService() {
            return mock(IEvaluationBoardService.class);
        }

        @Bean
        IEvaluationDimensionService evaluationDimensionService() {
            return mock(IEvaluationDimensionService.class);
        }

        @Bean
        IInterviewEvaluationService interviewEvaluationService() {
            return mock(IInterviewEvaluationService.class);
        }

        @Bean
        IUserService userService() {
            return mock(IUserService.class);
        }

        @Bean
        InterviewEvaluationController interviewEvaluationController(
                IEvaluationBoardService boardService,
                IEvaluationDimensionService dimensionService,
                IInterviewEvaluationService evaluationService,
                IUserService userService) {
            return new InterviewEvaluationController(boardService, dimensionService, evaluationService, userService);
        }

        @Bean
        InternalEvaluationController internalEvaluationController(
                IEvaluationBoardService boardService,
                IInterviewEvaluationService evaluationService) {
            return new InternalEvaluationController(boardService, evaluationService);
        }
    }

    @Autowired
    private InterviewEvaluationController controller;

    @Autowired
    private InternalEvaluationController internalController;

    @Autowired
    private IUserService userService;

    // --------------------------------------------------------- 管理端接口

    @Test
    @WithMockUser(authorities = "resume:audit")
    void 管理员可以开表并配置维度() {
        assertDoesNotThrow(() -> controller.openBoard(1));
        assertDoesNotThrow(() -> controller.setLocked(1, true));
        assertDoesNotThrow(() -> controller.summary(1));
        assertDoesNotThrow(() -> controller.saveDimensions(1, new SaveEvaluationDimensionsRequestDTO()));
    }

    @Test
    @WithMockUser(authorities = "interview:evaluate")
    void 面试官只能读评价表状态与维度不能开表或锁定() {
        assertDoesNotThrow(() -> controller.getBoard(1));
        assertDoesNotThrow(() -> controller.listDimensions(1));

        assertThrows(AccessDeniedException.class, () -> controller.openBoard(1));
        assertThrows(AccessDeniedException.class, () -> controller.setLocked(1, true));
        assertThrows(AccessDeniedException.class, () -> controller.summary(1));
        assertThrows(AccessDeniedException.class,
                () -> controller.saveDimensions(1, new SaveEvaluationDimensionsRequestDTO()));
    }

    @Test
    @WithMockUser(authorities = "resume:view")
    void 仅有查看简历权限的用户读不到评价表() {
        assertThrows(AccessDeniedException.class, () -> controller.getBoard(1));
        assertThrows(AccessDeniedException.class, () -> controller.openBoard(1));
    }

    /**
     * 面试官没有 resume:view，够不到简历库，只能走评价表这个入口；
     * 「他能看哪几个人」由服务层按场次绑定关系裁决，此处只验证接口本身对他开放。
     */
    @Test
    @WithMockUser(authorities = "interview:evaluate")
    void 面试官可以在评价表内速览候选人简历() {
        User viewer = new User();
        viewer.setUserId(7);
        when(userService.getUserByUsername(any())).thenReturn(viewer);

        assertDoesNotThrow(() -> controller.candidateResume(1, 100));
    }

    @Test
    @WithMockUser(authorities = "resume:view")
    void 没有评价权限的用户走不了评价表的简历入口() {
        assertThrows(AccessDeniedException.class, () -> controller.candidateResume(1, 100));
    }

    @Test
    void 未登录访问评价表被拒() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.getBoard(1));
    }

    // --------------------------------------------------------- 内部服务接口

    @Test
    @WithMockUser(roles = "INTERNAL_SERVICE")
    void 持有服务令牌的协同服务可以拉播种数据并物化() {
        assertDoesNotThrow(() -> internalController.seed(1));
        assertDoesNotThrow(() -> internalController.materialize(new MaterializeEvaluationRequestDTO()));
    }

    @Test
    @WithMockUser(authorities = "resume:audit")
    void 管理员的用户令牌不能直接调内部物化接口() {
        assertThrows(AccessDeniedException.class, () -> internalController.materialize(
                new MaterializeEvaluationRequestDTO()));
        assertThrows(AccessDeniedException.class, () -> internalController.seed(1));
    }
}
