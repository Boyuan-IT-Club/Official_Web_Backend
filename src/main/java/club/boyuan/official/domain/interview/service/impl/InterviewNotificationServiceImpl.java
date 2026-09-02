package club.boyuan.official.domain.interview.service.impl;

import java.util.List;
import club.boyuan.official.infra.notification.mail.MailTemplate;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.domain.interview.dto.InterviewBookingDTO;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewNotificationLog;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewNotificationLogMapper;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.messaging.InterviewNotificationMessage;
import club.boyuan.official.messaging.InterviewNotificationProducer;
import club.boyuan.official.infra.notification.InterviewNotificationEmailBuilder;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.domain.user.service.DepartmentService;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSlotService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.domain.interview.service.InterviewNotificationService;
import club.boyuan.official.domain.resume.service.ResumeDataService;
import club.boyuan.official.common.utils.MessageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewNotificationServiceImpl implements InterviewNotificationService {

    private static final int SCHEDULE_STATUS_ACTIVE = 1;
    private static final int DECISION_PASSED = 1;
    private static final int DECISION_REJECTED = 2;

    private final InterviewNotificationProducer notificationProducer;
    private final InterviewNotificationLogMapper notificationLogMapper;
    private final IInterviewScheduleService interviewScheduleService;
    private final IInterviewSlotService interviewSlotService;
    private final IResumeService resumeService;
    private final IUserService userService;
    private final InterviewResultMapper interviewResultMapper;
    private final DepartmentService departmentService;
    private final ResumeDataService resumeDataService;
    private final MessageUtils messageUtils;
    private final RecruitmentCycleMapper recruitmentCycleMapper;
    private final IRecruitmentQrCodeService qrCodeService;

    @Override
    public void enqueueBookingSuccess(Integer scheduleId, String requestId) {
        if (scheduleId == null) {
            return;
        }
        notificationProducer.publishBookingSuccess(scheduleId, requestId);
    }

    @Override
    public void dispatchReminders(InterviewNotificationType reminderType) {
        if (reminderType != InterviewNotificationType.EVE_REMINDER
                && reminderType != InterviewNotificationType.DAY_REMINDER) {
            return;
        }
        LocalDate targetDate = reminderType == InterviewNotificationType.EVE_REMINDER
                ? LocalDate.now().plusDays(1)
                : LocalDate.now();

        LocalDateTime dayStart = targetDate.atStartOfDay();
        LocalDateTime dayEnd = targetDate.atTime(LocalTime.MAX);

        LambdaQueryWrapper<InterviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSchedule::getStatus, SCHEDULE_STATUS_ACTIVE)
                .isNotNull(InterviewSchedule::getInterviewTime)
                .ge(InterviewSchedule::getInterviewTime, dayStart)
                .le(InterviewSchedule::getInterviewTime, dayEnd);

        for (InterviewSchedule schedule : interviewScheduleService.list(wrapper)) {
            if (alreadySent(reminderType, schedule.getScheduleId(), null)) {
                continue;
            }
            notificationProducer.publishReminder(reminderType, schedule.getScheduleId());
        }
        log.info("已扫描并投递 {} 提醒，目标日期={}", reminderType, targetDate);
    }

    @Override
    public void enqueueResultNotification(Integer resultId, String customBody) {
        if (resultId == null) {
            return;
        }
        notificationProducer.publishResult(resultId, customBody);
    }

    /**
     * 由 MQ 消费者调用：解析消息、发送邮件、记录日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deliver(InterviewNotificationMessage message) {
        if (message == null) {
            return;
        }
        InterviewNotificationType type = resolveType(message);
        if (type == null) {
            if (message.getResultId() != null && StringUtils.hasText(message.getCustomBody())) {
                deliverResult(null, message);
                return;
            }
            log.warn("无法解析通知类型，已忽略 message={}", message);
            return;
        }

        if (type == InterviewNotificationType.ADMISSION || type == InterviewNotificationType.REJECTION) {
            deliverResult(type, message);
            return;
        }

        Integer scheduleId = message.getScheduleId();
        if (scheduleId == null) {
            return;
        }
        if (alreadySent(type, scheduleId, null)) {
            log.info("通知已发送过，跳过 type={}, scheduleId={}", type, scheduleId);
            return;
        }

        InterviewSchedule schedule = interviewScheduleService.getById(scheduleId);
        if (schedule == null || !Integer.valueOf(SCHEDULE_STATUS_ACTIVE).equals(schedule.getStatus())) {
            log.info("预约不存在或已取消，跳过通知 scheduleId={}", scheduleId);
            return;
        }

        Resume resume = resumeService.getResumeById(schedule.getResumeId());
        String email = resume != null ? resumeDataService.getResumeEmail(resume) : null;
        String name = resume != null ? resumeDataService.getResumeName(resume) : null;
        if (!StringUtils.hasText(email)) {
            log.info("无有效邮箱，跳过通知 type={}, scheduleId={}", type, scheduleId);
            return;
        }

        InterviewSlot slot = interviewSlotService.getById(schedule.getSlotId());
        InterviewBookingDTO booking = InterviewBookingDTO.from(schedule, slot);

        String subject = InterviewNotificationEmailBuilder.subject(type);
        String body = InterviewNotificationEmailBuilder.body(type, name, booking, null);
        NoticeConfig reminderCfg = noticeConfig(booking == null ? null : booking.getCycleId());
        String html = InterviewNotificationEmailBuilder.html(
                type, name, booking, null, reminderCfg.academicYear(),
                reminderCfg.waitingRoom(), List.of(), reminderCfg.contactInfo()).html();
        sendAndLog(type, scheduleId, null, email, subject, body, html, schedule, message.getRequestId());
    }

    private void deliverResult(InterviewNotificationType type, InterviewNotificationMessage message) {
        Integer resultId = message.getResultId();
        if (resultId == null) {
            return;
        }
        boolean templated = !StringUtils.hasText(message.getCustomBody());
        if (templated) {
            if (type == null) {
                return;
            }
            if (alreadySent(type, null, resultId)) {
                log.info("结果通知已发送过，跳过 type={}, resultId={}", type, resultId);
                return;
            }
        }

        InterviewResult result = interviewResultMapper.selectById(resultId);
        if (result == null) {
            return;
        }

        User user = userService.getById(result.getUserId());
        if (user == null) {
            return;
        }

        Resume resume = null;
        InterviewSchedule schedule = interviewScheduleService.getById(result.getScheduleId());
        if (schedule != null) {
            resume = resumeService.getResumeById(schedule.getResumeId());
        }

        String email = resume != null ? resumeDataService.getResumeEmail(resume) : user.getEmail();
        String name = resume != null ? resumeDataService.getResumeName(resume) : user.getName();
        if (!StringUtils.hasText(email)) {
            log.warn("结果通知无邮箱 resultId={}", resultId);
            return;
        }

        String departmentName = resolveDepartmentName(result.getAssignedDeptId());
        InterviewBookingDTO booking = schedule != null
                ? InterviewBookingDTO.from(schedule, interviewSlotService.getById(schedule.getSlotId()))
                : null;

        InterviewNotificationType effectiveType = type != null ? type : InterviewNotificationType.REJECTION;
        String subject = templated
                ? InterviewNotificationEmailBuilder.subject(effectiveType)
                : "【博远信息技术社】面试结果通知";
        String body = templated
                ? InterviewNotificationEmailBuilder.body(effectiveType, name, booking, departmentName)
                : message.getCustomBody();

        // 管理员写了自定义正文时不套模板 —— 那是他要说的话，不该被包进
        // 「恭喜录取」的壳里
        String html = null;
        if (templated) {
            NoticeConfig cfg = noticeConfig(schedule == null ? null : schedule.getCycleId());
            List<MailTemplate.QrItem> qrs = effectiveType == InterviewNotificationType.ADMISSION
                    ? qrItems(schedule == null ? null : schedule.getCycleId(), result.getAssignedDeptId())
                    : List.of();
            html = InterviewNotificationEmailBuilder.html(
                    effectiveType, name, booking, departmentName,
                    cfg.academicYear(), cfg.waitingRoom(), qrs, cfg.contactInfo()).html();
        }

        sendAndLog(effectiveType, result.getScheduleId(), resultId, email, subject, body, html, schedule, null);
    }

    /** 邮件要用到的周期级配置。周期取不到时全部为空，模板会自动省略对应段落 */
    private record NoticeConfig(String academicYear, String waitingRoom, String contactInfo) {
        static NoticeConfig empty() {
            return new NoticeConfig(null, null, null);
        }
    }

    private NoticeConfig noticeConfig(Integer cycleId) {
        if (cycleId == null) {
            return NoticeConfig.empty();
        }
        RecruitmentCycle cycle = recruitmentCycleMapper.selectById(cycleId);
        if (cycle == null) {
            return NoticeConfig.empty();
        }
        return new NoticeConfig(cycle.getAcademicYear(), cycle.getWaitingRoom(), cycle.getContactInfo());
    }

    /**
     * 录取通知里要附的二维码：本人部门那张 + 大群那张。
     * 取不到就返回空列表 —— 模板会改成「登录官网查看」，而不是留一块空图。
     */
    private List<MailTemplate.QrItem> qrItems(Integer cycleId, Integer deptId) {
        if (cycleId == null) {
            return List.of();
        }
        try {
            return qrCodeService.forAdmitted(cycleId, deptId).stream()
                    .map(qr -> new MailTemplate.QrItem(qr.getImageUrl(), labelOf(qr)))
                    .toList();
        } catch (Exception e) {
            // 二维码取不到不该挡住录取通知本身
            log.warn("取二维码失败 cycleId={}, deptId={}: {}", cycleId, deptId, e.getMessage());
            return List.of();
        }
    }

    private String labelOf(RecruitmentQrCode qr) {
        if (StringUtils.hasText(qr.getRemark())) {
            return qr.getRemark();
        }
        if (RecruitmentQrCode.TYPE_MAIN_GROUP.equals(qr.getQrType())) {
            return "社团大群";
        }
        String dept = resolveDepartmentName(qr.getDeptId());
        return StringUtils.hasText(dept) ? dept + "群" : "部门群";
    }

    private InterviewNotificationType resolveType(InterviewNotificationMessage message) {
        if (message.getType() != null) {
            return message.getType();
        }
        if (message.getResultId() == null) {
            return null;
        }
        InterviewResult result = interviewResultMapper.selectById(message.getResultId());
        if (result == null || result.getDecision() == null) {
            return null;
        }
        return switch (result.getDecision()) {
            case DECISION_PASSED -> InterviewNotificationType.ADMISSION;
            case DECISION_REJECTED -> InterviewNotificationType.REJECTION;
            default -> null;
        };
    }

    private String resolveDepartmentName(Integer assignedDeptId) {
        if (assignedDeptId == null) {
            return null;
        }
        Department dept = departmentService.getById(assignedDeptId);
        return dept != null ? dept.getDeptName() : null;
    }

    private void sendAndLog(InterviewNotificationType type,
                            Integer scheduleId,
                            Integer resultId,
                            String email,
                            String subject,
                            String body,
                            String html,
                            InterviewSchedule schedule,
                            String requestId) {
        messageUtils.validateEmail(email);
        if (StringUtils.hasText(html)) {
            // HTML 为主、纯文本兜底：关掉 HTML 的客户端仍能读到完整内容
            messageUtils.sendHtmlEmail(email, subject, html, body);
        } else {
            messageUtils.sendEmail(email, subject, body);
        }

        InterviewNotificationLog logEntry = new InterviewNotificationLog()
                .setNotificationType(type.name())
                .setScheduleId(scheduleId)
                .setResultId(resultId)
                .setRecipientEmail(email)
                .setSentAt(LocalDateTime.now());
        notificationLogMapper.insert(logEntry);

        if (schedule != null && type == InterviewNotificationType.BOOKING_SUCCESS) {
            schedule.setNotifStatus(1);
            interviewScheduleService.updateById(schedule);
        }

        log.info("面试通知已发送 type={}, scheduleId={}, resultId={}, email={}, requestId={}",
                type, scheduleId, resultId, email, requestId);
    }

    private boolean alreadySent(InterviewNotificationType type, Integer scheduleId, Integer resultId) {
        LambdaQueryWrapper<InterviewNotificationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewNotificationLog::getNotificationType, type.name());
        if (scheduleId != null) {
            wrapper.eq(InterviewNotificationLog::getScheduleId, scheduleId);
        }
        if (resultId != null) {
            wrapper.eq(InterviewNotificationLog::getResultId, resultId);
        }
        return notificationLogMapper.selectCount(wrapper) > 0;
    }
}
