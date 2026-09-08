package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.utils.MessageUtils;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSlotService;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.resume.service.ResumeDataService;
import club.boyuan.official.domain.user.service.DepartmentService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.messaging.InterviewNotificationMessage;
import club.boyuan.official.messaging.InterviewNotificationProducer;
import club.boyuan.official.persistence.entity.InterviewNotificationLog;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewNotificationLogMapper;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 录取/未录取通知的重发与去重。
 *
 * 线上问题：管理员对同一个人第二次点「发送通知」，界面显示成功、notified_at 也更新了，
 * 邮箱里却没有。原因是消费端按 (type, resultId) 去重——同一份结果的模板邮件只投递一次，
 * 第二次静默跳过。
 *
 * 去重的本意只有一个：MQ 重投同一条消息时别发两遍。那对应的键是 requestId
 * （每次入队一个新 id），不是 resultId。这里用一个内存里的假日志表模拟真实去重：
 * 普通 mock 返回固定值分不出「按 requestId 查」和「按结果查」，老代码新代码都会绿。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewNotificationResendTest {

    @Mock private InterviewNotificationProducer notificationProducer;
    @Mock private InterviewNotificationLogMapper notificationLogMapper;
    @Mock private IInterviewScheduleService interviewScheduleService;
    @Mock private IInterviewSlotService interviewSlotService;
    @Mock private IResumeService resumeService;
    @Mock private IUserService userService;
    @Mock private InterviewResultMapper interviewResultMapper;
    @Mock private DepartmentService departmentService;
    @Mock private ResumeDataService resumeDataService;
    @Mock private MessageUtils messageUtils;
    @Mock private RecruitmentCycleMapper recruitmentCycleMapper;
    @Mock private IRecruitmentQrCodeService qrCodeService;

    @InjectMocks
    private InterviewNotificationServiceImpl service;

    /** 假日志表：insert 进来的行都记着，selectCount 按查询条件在里面数 */
    private final List<InterviewNotificationLog> logRows = new ArrayList<>();

    private static final int RESULT_ID = 42;

    @BeforeEach
    void setUp() {
        // 纯单测里没有 Spring 启动，MyBatis-Plus 不知道实体的列名，
        // LambdaQueryWrapper 一解析 sql 段就报 "can not find lambda cache"。手动注册一次。
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                InterviewNotificationLog.class);

        InterviewResult admitted = new InterviewResult()
                .setResultId(RESULT_ID).setUserId(7).setDecision(1)   // 1 = 录取
                .setScheduleId(null).setAssignedDeptId(null);          // 无场次、无部门：走最短的模板路径
        when(interviewResultMapper.selectById(RESULT_ID)).thenReturn(admitted);

        User u = new User();
        u.setUserId(7);
        u.setName("张三");
        u.setEmail("zhangsan@stu.ecnu.edu.cn");
        when(userService.getById(7)).thenReturn(u);
        when(interviewScheduleService.getById(any())).thenReturn(null);

        when(notificationLogMapper.insert(any(InterviewNotificationLog.class))).thenAnswer(inv -> {
            logRows.add(inv.getArgument(0));
            return 1;
        });
        // 按 wrapper 里实际查的列去数：request_id 还是 result_id
        when(notificationLogMapper.selectCount(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<?> w = inv.getArgument(0);
            String sql = w.getSqlSegment();
            List<Object> params = new ArrayList<>(w.getParamNameValuePairs().values());
            if (sql.contains("request_id")) {
                Object rid = params.get(0);
                return logRows.stream().filter(r -> Objects.equals(r.getRequestId(), rid)).count();
            }
            // (notification_type, result_id) 那条老路径
            Object resultId = params.stream().filter(p -> p instanceof Integer).findFirst().orElse(null);
            return logRows.stream().filter(r -> Objects.equals(r.getResultId(), resultId)).count();
        });
    }

    private static InterviewNotificationMessage templated(String requestId) {
        // type=null 让服务自己从 decision 解析；customBody=null 即模板邮件
        return new InterviewNotificationMessage(null, null, RESULT_ID, requestId, null);
    }

    @Test
    @DisplayName("管理员第二次发送（新 requestId）必须真的再发一封")
    void resendWithNewRequestIdDeliversAgain() {
        service.deliver(templated("req-1"));
        service.deliver(templated("req-2"));   // 第二次点「发送通知」

        // 老代码按 (type, resultId) 去重，第二封会被静默吞掉——界面成功、邮箱没有
        verify(messageUtils, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("MQ 重投同一条消息（同 requestId）只发一封")
    void redeliveryOfSameMessageIsDeduplicated() {
        InterviewNotificationMessage m = templated("req-same");
        service.deliver(m);
        service.deliver(m);   // acknowledge-mode: auto 下 SMTP 超时就会这样重投

        verify(messageUtils, times(1)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("日志里记下 requestId，去重才有依据")
    void logRowCarriesRequestId() {
        service.deliver(templated("req-logged"));
        org.junit.jupiter.api.Assertions.assertEquals(1, logRows.size());
        org.junit.jupiter.api.Assertions.assertEquals("req-logged", logRows.get(0).getRequestId());
        org.junit.jupiter.api.Assertions.assertEquals(RESULT_ID, logRows.get(0).getResultId());
    }

    @Test
    @DisplayName("旧格式消息（无 requestId）退回按结果去重，部署窗口内不失去保护")
    void legacyMessageWithoutRequestIdStillDedupesByResult() {
        // 部署前已入队、仍在队列里的消息没有 requestId
        service.deliver(templated(null));
        service.deliver(templated(null));

        verify(messageUtils, times(1)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("自定义正文不受去重影响，每次都发")
    void customBodyAlwaysDelivers() {
        InterviewNotificationMessage custom =
                new InterviewNotificationMessage(null, null, RESULT_ID, "req-c1", "请于周五来签约");
        service.deliver(custom);
        service.deliver(new InterviewNotificationMessage(null, null, RESULT_ID, "req-c2", "请于周五来签约"));

        // 自定义正文走的是纯文本 sendEmail
        verify(messageUtils, times(2)).sendEmail(anyString(), anyString(), anyString());
        verify(messageUtils, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }
}
