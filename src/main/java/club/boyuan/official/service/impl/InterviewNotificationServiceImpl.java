package club.boyuan.official.service.impl;

import club.boyuan.official.dto.InterviewBookingDTO;
import club.boyuan.official.entity.Department;
import club.boyuan.official.entity.InterviewNotificationLog;
import club.boyuan.official.entity.InterviewResult;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.User;
import club.boyuan.official.mapper.InterviewNotificationLogMapper;
import club.boyuan.official.mapper.InterviewResultMapper;
import club.boyuan.official.messaging.InterviewNotificationMessage;
import club.boyuan.official.messaging.InterviewNotificationProducer;
import club.boyuan.official.notification.InterviewNotificationEmailBuilder;
import club.boyuan.official.notification.InterviewNotificationType;
import club.boyuan.official.service.DepartmentService;
import club.boyuan.official.service.IInterviewScheduleService;
import club.boyuan.official.service.IInterviewSlotService;
import club.boyuan.official.service.IResumeService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.InterviewNotificationService;
import club.boyuan.official.service.ResumeDataService;
import club.boyuan.official.utils.MessageUtils;
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
        sendAndLog(type, scheduleId, null, email, subject, body, schedule, message.getRequestId());
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

        sendAndLog(effectiveType, result.getScheduleId(), resultId, email, subject, body, schedule, null);
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
                            InterviewSchedule schedule,
                            String requestId) {
        messageUtils.validateEmail(email);
        messageUtils.sendEmail(email, subject, body);

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
