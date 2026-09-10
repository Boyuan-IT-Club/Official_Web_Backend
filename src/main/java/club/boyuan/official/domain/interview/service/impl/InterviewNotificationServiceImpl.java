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

    @Override
    public void enqueueResumeRejectedNotification(Integer resumeId, String customBody) {
        if (resumeId == null) {
            return;
        }
        notificationProducer.publishResumeRejected(resumeId, customBody);
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

        if (type == InterviewNotificationType.RESUME_REJECTED) {
            deliverResumeRejected(message);
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
        /*
         * 管理员填的内容是「补充」而不是「替换」。
         *
         * 早先的实现把 customBody 当整封正文：管理员随手写两句，学生收到的
         * 录取信里就只剩那两句——模板里的祝贺、分配部门、入群二维码全没了。
         * 现在一律走模板，customBody 以「社团补充说明」附在正文之后。
         *
         * 例外：decision 不是通过/未通过（待定、待调剂）时没有对应模板，
         * 此时仍按管理员写的纯文本发——这种场景本来就是「我要单独说点事」。
         */
        String extraNote = message.getCustomBody();
        boolean templated = type != null;
        if (!templated && !StringUtils.hasText(extraNote)) {
            return;
        }
        /*
         * 去重按「这一次发送」而不是按「这份结果」。
         *
         * 去重的目的只有一个：MQ 重投（acknowledge-mode: auto 下 SMTP 超时会触发）
         * 不该让同一封邮件发两遍。那对应的键是 requestId——同一条消息重投，id 相同。
         * 管理员点第二次「发送通知」是一次新的入队、新的 id，理应放行。
         *
         * 原来按 (type, resultId) 去重，把这两种情况混为一谈：录取邮件只能发一次，
         * 第二次静默跳过，而上层照样返回成功、更新 notified_at——界面「已通知」，
         * 邮箱里没有。
         *
         * 没有 requestId 的消息（部署前已入队、仍在队列里的旧消息）退回旧逻辑，
         * 免得那一小段窗口里重投失去保护。
         */
        String requestId = message.getRequestId();
        if (StringUtils.hasText(requestId)) {
            if (alreadySentByRequest(requestId)) {
                log.info("同一次发送已投递过（MQ 重投），跳过 requestId={}, resultId={}", requestId, resultId);
                return;
            }
        } else if (alreadySent(type, null, resultId)) {
            log.info("旧格式消息且结果通知已发送过，跳过 type={}, resultId={}", type, resultId);
            return;
        }

        InterviewResult result = interviewResultMapper.selectById(resultId);
        if (result == null) {
            return;
        }

        User user = userService.getById(result.getUserId());
        if (user == null) {
            return;
        }

        InterviewSchedule schedule = interviewScheduleService.getById(result.getScheduleId());
        // V34 起结果可以不挂面试安排（如无法线下参加的同学），这时简历要从
        // 结果自带的 resume_id 找，姓名与收件邮箱才是简历里填的那份
        Integer resumeId = schedule != null ? schedule.getResumeId() : result.getResumeId();
        Resume resume = resumeId != null ? resumeService.getResumeById(resumeId) : null;

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
                ? InterviewNotificationEmailBuilder.body(
                        effectiveType, name, booking, departmentName, extraNote)
                : extraNote;

        // 无模板可用（待定/待调剂）时才发纯文本，其余一律模板 + 补充说明
        String html = null;
        if (templated) {
            // 周期号优先取结果自带的（V34 起无安排的结果也有 cycle_id），
            // 老数据没这列时再退回从面试安排上取。之前只从安排上取，
            // 无安排的录取通知会整封丢掉二维码和候场/联系方式配置
            Integer cycleId = result.getCycleId() != null ? result.getCycleId()
                    : (schedule != null ? schedule.getCycleId() : null);
            NoticeConfig cfg = noticeConfig(cycleId);
            List<MailTemplate.QrItem> qrs = effectiveType == InterviewNotificationType.ADMISSION
                    ? qrItems(cycleId, result.getAssignedDeptId())
                    : List.of();
            html = InterviewNotificationEmailBuilder.html(
                    effectiveType, name, booking, departmentName,
                    cfg.academicYear(), cfg.waitingRoom(), qrs, cfg.contactInfo(), extraNote).html();
        }

        /*
         * 日志不写 scheduleId —— 这里是修「重发变成连发 3 封」的关键。
         *
         * uk_type_schedule（类型 × 场次唯一）是给场次类通知用的：预约成功、
         * 面试前提醒由系统自动触发，一场一封才是对的语义（见 V37 的说明）。
         * 而结果通知是管理员有意发的、允许重发，它由 result_id + request_id
         * 唯一确定，本来就不该占场次的槽位。
         *
         * 之前把 result.getScheduleId() 一起写进去，于是给「有面试安排」的同学
         * 重发同类型结果通知时撞键：邮件已发出 → 写日志抛 DuplicateKey →
         * 消息被判消费失败 → MQ 按 max-attempts:3 重投 → 每次重投再发一封，
         * 收件人精确收到 3 封（线上实测：A/B 两台日志里都是这个异常栈）。
         * 无面试安排的同学 scheduleId 为空、MySQL 唯一键不管 NULL，所以没事。
         *
         * 这一行改动之后，历史行照旧保留，新行 scheduleId 为空，不再有冲突；
         * 结果对应的场次可经 interview_result.schedule_id 反查，信息不丢。
         */
        sendAndLog(effectiveType, null, resultId, email, subject, body, html, schedule,
                message.getRequestId());
    }

    /**
     * 简历未通过初筛的通知。
     *
     * 与结果通知的差别：这时还没有面试安排、没有结果行，收件人只能从简历定位；
     * 周期配置里只用得上「本届负责人联系方式」（没有面试时间地点可言）。
     * 去重同样按 requestId——管理员想重发就能重发。
     */
    private void deliverResumeRejected(InterviewNotificationMessage message) {
        Integer resumeId = message.getResumeId();
        if (resumeId == null) {
            return;
        }
        String requestId = message.getRequestId();
        if (StringUtils.hasText(requestId) && alreadySentByRequest(requestId)) {
            log.info("同一次发送已投递过（MQ 重投），跳过 requestId={}, resumeId={}", requestId, resumeId);
            return;
        }

        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            return;
        }
        String email = resumeDataService.getResumeEmail(resume);
        String name = resumeDataService.getResumeName(resume);
        if (!StringUtils.hasText(email)) {
            User user = resume.getUserId() == null ? null : userService.getById(resume.getUserId());
            email = user != null ? user.getEmail() : null;
            if (name == null && user != null) {
                name = user.getName();
            }
        }
        if (!StringUtils.hasText(email)) {
            log.warn("简历初筛通知无邮箱 resumeId={}", resumeId);
            return;
        }

        NoticeConfig cfg = noticeConfig(resume.getCycleId());
        InterviewNotificationType type = InterviewNotificationType.RESUME_REJECTED;
        String subject = InterviewNotificationEmailBuilder.subject(type);
        String body = InterviewNotificationEmailBuilder.body(
                type, name, null, null, message.getCustomBody());
        String html = InterviewNotificationEmailBuilder.html(
                type, name, null, null, cfg.academicYear(), null, List.of(),
                cfg.contactInfo(), message.getCustomBody()).html();

        sendAndLog(type, null, null, email, subject, body, html, null, requestId);
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

        /*
         * 分界线：以上邮件已经真的发出去了，以下都是记账。
         *
         * 记账失败绝不能抛出去——@RabbitListener 的 acknowledge-mode: auto 下
         * 任何异常都会被判为消费失败并按 max-attempts 重投，而重投会把这封
         * 已经送达的邮件再发一遍。用户的收件箱不该为我们写不进一行日志买单。
         * 失败只记 error 日志，人工可从邮件服务商侧核对。
         */
        try {
            InterviewNotificationLog logEntry = new InterviewNotificationLog()
                    .setNotificationType(type.name())
                    .setScheduleId(scheduleId)
                    .setResultId(resultId)
                    .setRequestId(requestId)
                    .setRecipientEmail(email)
                    .setSentAt(LocalDateTime.now());
            notificationLogMapper.insert(logEntry);

            if (schedule != null && type == InterviewNotificationType.BOOKING_SUCCESS) {
                schedule.setNotifStatus(1);
                interviewScheduleService.updateById(schedule);
            }
        } catch (Exception e) {
            log.error("邮件已发送但记录发送日志失败（不重投，避免重复发信）"
                    + " type={}, scheduleId={}, resultId={}, email={}, requestId={}",
                    type, scheduleId, resultId, email, requestId, e);
        }

        log.info("面试通知已发送 type={}, scheduleId={}, resultId={}, email={}, requestId={}",
                type, scheduleId, resultId, email, requestId);
    }

    /** 按本次发送的 requestId 判重——只拦 MQ 重投同一条消息。 */
    private boolean alreadySentByRequest(String requestId) {
        return notificationLogMapper.selectCount(new LambdaQueryWrapper<InterviewNotificationLog>()
                .eq(InterviewNotificationLog::getRequestId, requestId)) > 0;
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
