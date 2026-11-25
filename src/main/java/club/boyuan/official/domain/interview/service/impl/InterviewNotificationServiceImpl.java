package club.boyuan.official.domain.interview.service.impl;

import java.util.List;
import club.boyuan.official.infra.notification.mail.MailTemplate;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.domain.interview.dto.InterviewBookingDTO;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
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
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewNotificationServiceImpl implements InterviewNotificationService {

    private static final int SCHEDULE_STATUS_ACTIVE = 1;
    /** 简历状态：未通过初筛（与 ResumeServiceImpl.STATUS_SCREEN_REJECTED 一致） */
    private static final int RESUME_STATUS_SCREEN_REJECTED = 5;
    private static final int DECISION_PASSED = 1;
    private static final int DECISION_REJECTED = 2;

    private final InterviewNotificationProducer notificationProducer;
    private final club.boyuan.official.messaging.DeliveryLedger ledger;
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
    private final InterviewSessionMapper interviewSessionMapper;

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
            notificationProducer.publishReminder(reminderType, schedule.getScheduleId());
        }
        log.info("已扫描并投递 {} 提醒，目标日期={}", reminderType, targetDate);
    }

    @Override
    public boolean enqueueResultNotification(Integer resultId, String customBody, String requestId) {
        return resultId != null && notificationProducer.publishResult(resultId, customBody, requestId);
    }

    @Override
    public boolean enqueueResumeRejectedNotification(Integer resumeId, String customBody, String requestId) {
        return resumeId != null && notificationProducer.publishResumeRejected(resumeId, customBody, requestId);
    }

    public record PreparedMail(String address, String subject, String body, String html, LocalDateTime interviewTime) {}

    public void deliver(InterviewNotificationMessage envelope) {
        deliver(envelope, null);
    }

    public void deliver(InterviewNotificationMessage envelope, String brokerId) {
        if (envelope == null) return;
        InterviewNotificationMessage message = ledger.observe(envelope, brokerId);
        if (message == null) return;
        PreparedMail mail = prepare(message);
        if (mail == null) {
            ledger.reject(message, "SKIPPED", "Recipient or business record no longer eligible");
            return;
        }
        try {
            messageUtils.validateEmail(mail.address());
        } catch (RuntimeException e) {
            ledger.reject(message, "FAILED", "Invalid recipient address");
            return;
        }
        String token = java.util.UUID.randomUUID().toString();
        if (!ledger.claim(message, token, mail.address(), mail.interviewTime())) return;
        try {
            messageUtils.sendHtmlEmail(mail.address(), mail.subject(), mail.html(), mail.body());
        } catch (RuntimeException e) {
            ledger.finish(message, token, false, e.getClass().getSimpleName());
            return;
        }
        ledger.finish(message, token, true, null);
    }

    PreparedMail prepare(InterviewNotificationMessage message) {
        InterviewNotificationType type = message.getType();
        if (type == null) return null;
        if (type == InterviewNotificationType.ADMISSION || type == InterviewNotificationType.REJECTION) {
            return prepareResult(type, message);
        }
        if (type == InterviewNotificationType.RESUME_REJECTED) return prepareScreened(message);
        Integer scheduleId = message.getScheduleId();
        if (scheduleId == null) return null;
        InterviewSchedule schedule = interviewScheduleService.getById(scheduleId);
        if (schedule == null || !Integer.valueOf(SCHEDULE_STATUS_ACTIVE).equals(schedule.getStatus())) {
            log.info("预约不存在或已取消，跳过通知 scheduleId={}", scheduleId);
            return null;
        }
        /*
         * 线上面试不走邮件通知，和「简历里就选只能线上」的同学同一套处理：
         * 时间是管理员私下和本人约的，系统这边既没有权威时间也没有教室可写，
         * 发一封模板信只会和私聊里说好的对不上。他们待在「待约线上面试」名单里。
         */
        if (Integer.valueOf(1).equals(schedule.getInterviewMode())) {
            log.info("线上面试不发通知，跳过 type={}, scheduleId={}", type, scheduleId);
            return null;
        }

        Resume resume = resumeService.getResumeById(schedule.getResumeId());
        /*
         * 初筛未通过的人不再收面试相关邮件。
         *
         * 面试安排是初筛之前排的，初筛后那条安排还在（status 仍是 1），
         * 而提醒的定时任务只看「面试时间 + 安排生效」，不看简历状态——
         * 于是一个刚收到「未通过初筛」的同学，当天还会再收到一封
         * 「明天来面试」。线上就差点这么发出去（丁华烨已初筛未通过，
         * 却仍占着次日 09:05 的场次）。
         *
         * 拦在这里而不是拦在定时任务里：手动补发走的也是这条路径，
         * 两边都要拦，放在投递前的统一检查点只写一次。
         */
        if (resume != null && Integer.valueOf(RESUME_STATUS_SCREEN_REJECTED).equals(resume.getStatus())) {
            log.info("简历已初筛未通过，跳过面试类通知 type={}, scheduleId={}, resumeId={}",
                    type, scheduleId, resume.getResumeId());
            return null;
        }
        String email = resume != null ? resumeDataService.getResumeEmail(resume) : null;
        String name = resume != null ? resumeDataService.getResumeName(resume) : null;
        if (!StringUtils.hasText(email)) {
            log.info("无有效邮箱，跳过通知 type={}, scheduleId={}", type, scheduleId);
            return null;
        }

        InterviewBookingDTO booking = bookingOf(schedule);

        String subject = InterviewNotificationEmailBuilder.subject(type);
        String body = InterviewNotificationEmailBuilder.body(type, name, booking, null);
        NoticeConfig reminderCfg = noticeConfig(booking == null ? null : booking.getCycleId());
        // 线上没有「先到某个教室等」这回事，候场教室那一行不该出现
        String waitingRoom = Integer.valueOf(1).equals(schedule.getInterviewMode())
                ? null : reminderCfg.waitingRoom();
        String html = InterviewNotificationEmailBuilder.html(
                type, name, booking, null, reminderCfg.academicYear(),
                waitingRoom, List.of(), reminderCfg.contactInfo()).html();
        return new PreparedMail(email, subject, body, html, schedule.getInterviewTime());
    }

    /**
     * 邮件里的面试时间 / 房间。
     *
     * InterviewBookingDTO.from 只会从 interview_slot（方案A 秒杀的时段表）取地点，
     * 而现行方案B 的安排挂在场次上：slot_id 为空、session_id 指向 interview_session，
     * 房间存在场次的 location 里。原来只查 slot，于是方案B 的每一封「面试安排通知 /
     * 前一天提醒」房间都是「待通知」（2026 届 #14 周期 97 封全中）。
     */
    InterviewBookingDTO bookingOf(InterviewSchedule schedule) {
        InterviewSlot slot = schedule.historicalSlot();
        if (slot == null && schedule.getSlotId() != null) slot = interviewSlotService.getById(schedule.getSlotId());
        InterviewBookingDTO booking = InterviewBookingDTO.from(schedule, slot);
        /*
         * 线上面试没有教室，地点就是会议链接（周期级统一配置）。
         * 同意「改为线上」时会解绑 session，所以下面那段按场次取房间的逻辑
         * 本来也取不到东西；这里直接给链接，邮件模板的 locationOf 会优先用
         * location、为空时退回 meetingLink。
         */
        if (Integer.valueOf(1).equals(schedule.getInterviewMode())) {
            RecruitmentCycle cycle = schedule.getCycleId() == null
                    ? null : recruitmentCycleMapper.selectById(schedule.getCycleId());
            if (cycle != null && StringUtils.hasText(cycle.getOnlineMeetingLink())) {
                booking.setMeetingLink(cycle.getOnlineMeetingLink().trim());
            }
            return booking;
        }
        if (schedule.getSessionId() != null) {
            InterviewSession session = interviewSessionMapper.selectById(schedule.getSessionId());
            if (session != null && StringUtils.hasText(session.getLocation())) {
                booking.setLocation(session.getLocation());
            }
        }
        return booking;
    }

    private PreparedMail prepareResult(InterviewNotificationType type, InterviewNotificationMessage message) {
        Integer resultId = message.getResultId();
        if (resultId == null) return null;
        InterviewResult result = interviewResultMapper.selectById(resultId);
        int decision = type == InterviewNotificationType.ADMISSION ? DECISION_PASSED : DECISION_REJECTED;
        if (result == null || !Integer.valueOf(decision).equals(result.getDecision())) return null;
        String extraNote = message.getCustomBody();
        User user = userService.getById(result.getUserId());
        if (user == null) {
            return null;
        }

        InterviewSchedule schedule = result.getScheduleId() == null ? null : interviewScheduleService.getById(result.getScheduleId());
        // V34 起结果可以不挂面试安排（如无法线下参加的同学），这时简历要从
        // 结果自带的 resume_id 找，姓名与收件邮箱才是简历里填的那份
        Integer resumeId = schedule != null ? schedule.getResumeId() : result.getResumeId();
        Resume resume = resumeId != null ? resumeService.getResumeById(resumeId) : null;

        String email = resume != null ? resumeDataService.getResumeEmail(resume) : user.getEmail();
        String name = resume != null ? resumeDataService.getResumeName(resume) : user.getName();
        if (!StringUtils.hasText(email)) {
            log.warn("结果通知无邮箱 resultId={}", resultId);
            return null;
        }

        String departmentName = resolveDepartmentName(result.getAssignedDeptId());
        InterviewBookingDTO booking = schedule != null
                ? bookingOf(schedule)
                : null;

        String subject = InterviewNotificationEmailBuilder.subject(type);
        String body = InterviewNotificationEmailBuilder.body(type, name, booking, departmentName, extraNote);
        Integer cycleId = result.getCycleId() != null ? result.getCycleId()
                : (schedule != null ? schedule.getCycleId() : null);
        NoticeConfig cfg = noticeConfig(cycleId);
        List<MailTemplate.QrItem> qrs = type == InterviewNotificationType.ADMISSION
                ? qrItems(cycleId, result.getAssignedDeptId())
                : List.of();
        String html = InterviewNotificationEmailBuilder.html(
                type, name, booking, departmentName,
                cfg.academicYear(), cfg.waitingRoom(), qrs, cfg.contactInfo(), extraNote).html();
        return new PreparedMail(email, subject, body, html, schedule == null ? null : schedule.getInterviewTime());
    }

    private PreparedMail prepareScreened(InterviewNotificationMessage message) {
        Integer resumeId = message.getResumeId();
        if (resumeId == null) {
            return null;
        }
        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null || !Integer.valueOf(RESUME_STATUS_SCREEN_REJECTED).equals(resume.getStatus())) {
            return null;
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
            return null;
        }

        NoticeConfig cfg = noticeConfig(resume.getCycleId());
        InterviewNotificationType type = InterviewNotificationType.RESUME_REJECTED;
        String subject = InterviewNotificationEmailBuilder.subject(type);
        String body = InterviewNotificationEmailBuilder.body(
                type, name, null, null, message.getCustomBody());
        String html = InterviewNotificationEmailBuilder.html(
                type, name, null, null, cfg.academicYear(), null, List.of(),
                cfg.contactInfo(), message.getCustomBody()).html();

        return new PreparedMail(email, subject, body, html, null);
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
     * 录取通知里要附的二维码：本人部门那张 + 大群（有第二个大群时两张都附）。
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
        if (RecruitmentQrCode.TYPE_MAIN_GROUP_2.equals(qr.getQrType())) {
            return "社团大群 2";
        }
        String dept = resolveDepartmentName(qr.getDeptId());
        return StringUtils.hasText(dept) ? dept + "群" : "部门群";
    }

    private String resolveDepartmentName(Integer assignedDeptId) {
        if (assignedDeptId == null) {
            return null;
        }
        Department dept = departmentService.getById(assignedDeptId);
        return dept != null ? dept.getDeptName() : null;
    }

}
