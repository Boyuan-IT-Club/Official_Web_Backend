package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.domain.interview.dto.BatchDecisionRequestDTO;
import club.boyuan.official.domain.interview.dto.InterviewResultSaveDTO;
import club.boyuan.official.domain.interview.dto.SendNotificationsRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewResultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 录取结果接口的权限边界。
 * <p>
 * 这个类此前没有任何鉴权注解，只落到 SecurityConfig 的 {@code anyRequest().authenticated()}——
 * 也就是任何登录用户（包括投了简历的学生本人）都能调 {@code PUT /update/{resultId}}
 * 把自己改成「通过」并指定录取部门，还能群发通知、拉取全体候选人的结果名单。
 * 这些断言就是钉住那个修复，防止后来者再加接口时忘掉类级 {@code @PreAuthorize}。
 * <p>
 * 沿用 {@link EvaluationApiSecurityTest} 的做法：只装配控制器与方法级鉴权，不启动整个应用，
 * 因为权限注解是否生效与数据库无关。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = InterviewResultApiSecurityTest.TestConfig.class)
class InterviewResultApiSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        IInterviewResultService interviewResultService() {
            return mock(IInterviewResultService.class);
        }

        @Bean
        InterviewResultController interviewResultController(IInterviewResultService service) {
            return new InterviewResultController(service);
        }
    }

    @Autowired
    private InterviewResultController controller;

    private static SendNotificationsRequestDTO notifyRequest() {
        // 字段填齐：控制器里 getResultIds().size() 会对空 DTO 抛 NPE 并打一整屏栈，
        // 那与本测试要断言的鉴权无关，只会污染测试输出
        SendNotificationsRequestDTO request = new SendNotificationsRequestDTO();
        request.setResultIds(List.of(10));
        request.setNotificationType("email");
        return request;
    }

    private static BatchDecisionRequestDTO batchRequest() {
        BatchDecisionRequestDTO request = new BatchDecisionRequestDTO();
        request.setCycleId(1);
        request.setResultIds(List.of(10, 11));
        request.setDecision(1);
        request.setAssignedDeptId(3);
        return request;
    }

    @Test
    @WithMockUser(authorities = "resume:audit")
    void 管理员可以批量录取并逐条录入结果() {
        assertDoesNotThrow(() -> controller.batchDecision(batchRequest()));
        assertDoesNotThrow(() -> controller.update(10, new InterviewResultSaveDTO()));
        assertDoesNotThrow(() -> controller.list(1, null, null, null, 1, 10));
        assertDoesNotThrow(() -> controller.sendNotifications(notifyRequest()));
    }

    /**
     * 面试官负责打分，录取与否是管理员的决定——评价权限不该顺带获得改结果的能力。
     */
    @Test
    @WithMockUser(authorities = "interview:evaluate")
    void 面试官改不了录取结果也发不了通知() {
        assertThrows(AccessDeniedException.class, () -> controller.batchDecision(batchRequest()));
        assertThrows(AccessDeniedException.class, () -> controller.update(10, new InterviewResultSaveDTO()));
        assertThrows(AccessDeniedException.class, () -> controller.list(1, null, null, null, 1, 10));
        assertThrows(AccessDeniedException.class, () -> controller.sendNotifications(notifyRequest()));
    }

    /**
     * 最要紧的一条：普通登录用户（学生）不能把自己改成录取，也不能读到全体结果名单。
     */
    @Test
    @WithMockUser(authorities = "resume:view")
    void 普通登录用户既改不了结果也读不到名单() {
        assertThrows(AccessDeniedException.class, () -> controller.batchDecision(batchRequest()));
        assertThrows(AccessDeniedException.class, () -> controller.update(10, new InterviewResultSaveDTO()));
        assertThrows(AccessDeniedException.class, () -> controller.list(1, null, null, null, 1, 10));
        assertThrows(AccessDeniedException.class, () -> controller.get(10));
    }
}
