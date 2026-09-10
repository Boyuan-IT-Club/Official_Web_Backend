package club.boyuan.official.domain.interview.service;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.NotificationCenterDTO;
import club.boyuan.official.domain.resume.service.impl.ResumeServiceImpl;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.persistence.entity.*;
import club.boyuan.official.persistence.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知中心的读模型：把四类对外邮件的进度算出来，并给出初筛未通过名单。
 *
 * 都是批量查询，不做 N+1：名单最长也就一届的投递量，一次拉全比逐条查稳。
 */
@Service
@RequiredArgsConstructor
public class NotificationCenterService {

    /** 简历里这些字段名对应学号；不同届表头写法不一样，都认 */
    private static final List<String> STUDENT_ID_LABELS = List.of("学号", "学号/工号", "学号 / 工号");

    private final ResumeMapper resumeMapper;
    private final ResumeFieldDefinitionMapper fieldDefinitionMapper;
    private final ResumeFieldValueMapper fieldValueMapper;
    private final UserMapper userMapper;
    private final InterviewScheduleMapper scheduleMapper;
    private final InterviewResultMapper resultMapper;
    private final InterviewNotificationLogMapper notificationLogMapper;
    private final InterviewSessionMapper sessionMapper;
    private final DepartmentMapper departmentMapper;
    private final club.boyuan.official.messaging.InterviewNotificationProducer notificationProducer;

    public NotificationCenterDTO overview(Integer cycleId) {
        List<Resume> rejected = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getCycleId, cycleId)
                .eq(Resume::getStatus, ResumeServiceImpl.STATUS_SCREEN_REJECTED));

        Map<Integer, LocalDateTime> rejectNotified = latestSentByResume(
                rejected.stream().map(Resume::getResumeId).toList());

        List<NotificationCenterDTO.ScreenedOutItem> screenedOut = buildScreenedOut(cycleId, rejected, rejectNotified);

        long rejectedSent = screenedOut.stream().filter(i -> i.getNotifiedAt() != null).count();

        // 面试通知：生效安排都该收到；notif_status=1 表示「安排通知」已送达
        List<InterviewSchedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<InterviewSchedule>()
                .eq(InterviewSchedule::getCycleId, cycleId)
                .eq(InterviewSchedule::getStatus, 1));
        Set<Integer> scheduleIds = schedules.stream()
                .map(InterviewSchedule::getScheduleId).collect(Collectors.toSet());
        long arrangedSent = schedules.stream()
                .filter(s -> Integer.valueOf(1).equals(s.getNotifStatus())).count();

        // 结果通知：只有已录入决定的人才该收到，「待定」不发
        List<InterviewResult> results = resultMapper.selectList(new LambdaQueryWrapper<InterviewResult>()
                .eq(InterviewResult::getCycleId, cycleId)
                .ne(InterviewResult::getDecision, 0));
        long resultSent = results.stream().filter(r -> r.getNotifiedAt() != null).count();

        Set<Integer> eveSent = sentScheduleIds(InterviewNotificationType.EVE_REMINDER, scheduleIds);
        Set<Integer> daySent = sentScheduleIds(InterviewNotificationType.DAY_REMINDER, scheduleIds);

        return NotificationCenterDTO.builder()
                .resumeRejected(bucket(screenedOut.size(), rejectedSent))
                .interviewArranged(bucket(schedules.size(), arrangedSent))
                .eveReminder(bucket(schedules.size(), eveSent.size()))
                .dayReminder(bucket(schedules.size(), daySent.size()))
                .result(bucket(results.size(), resultSent))
                .screenedOut(screenedOut)
                .schedules(buildScheduleNotices(cycleId, schedules, eveSent, daySent))
                .build();
    }

    /**
     * 手动补发挂在面试安排上的通知（安排通知 / 前一天提醒 / 当天提醒）。
     *
     * 这三类平时由系统触发：排上场次时发安排通知，定时任务按面试日期发提醒。
     * 但会漏——手动改过面试时间、场次是提醒跑完之后才排的、MQ 那阵子堵了，
     * 管理员发现「待发 6」却没有任何办法补，只能干等下一次定时。
     *
     * 只投递没发过的：消费端本来就有 alreadySent 去重，这里先滤一遍是为了
     * 能如实告诉管理员「这次发了几个、几个本来就发过」，而不是投进去石沉大海。
     */
    public Map<String, Object> sendScheduleNotices(Integer cycleId, InterviewNotificationType type,
                                                   List<Integer> scheduleIds) {
        if (type != InterviewNotificationType.BOOKING_SUCCESS
                && type != InterviewNotificationType.EVE_REMINDER
                && type != InterviewNotificationType.DAY_REMINDER) {
            throw new BusinessException(BusinessExceptionEnum.ILLEGAL_ARGUMENT,
                    "这类通知不能按面试安排发送");
        }
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "请提供 scheduleIds");
        }

        // 归属校验：只认属于本周期且仍生效的安排，免得请求里夹带别届的 id
        Set<Integer> valid = scheduleMapper.selectList(new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .eq(InterviewSchedule::getStatus, 1)
                        .in(InterviewSchedule::getScheduleId, scheduleIds))
                .stream().map(InterviewSchedule::getScheduleId).collect(Collectors.toSet());

        Set<Integer> sent = sentScheduleIds(type, valid);
        List<Integer> queued = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();
        for (Integer id : scheduleIds) {
            if (!valid.contains(id) || sent.contains(id)) {
                skipped.add(id);
                continue;
            }
            if (type == InterviewNotificationType.BOOKING_SUCCESS) {
                notificationProducer.publishBookingSuccess(id, java.util.UUID.randomUUID().toString());
            } else {
                notificationProducer.publishReminder(type, id);
            }
            queued.add(id);
        }
        return Map.of("queued", queued.size(), "skipped", skipped);
    }

    private NotificationCenterDTO.Bucket bucket(long total, long sent) {
        return NotificationCenterDTO.Bucket.builder()
                .total(total).sent(sent).pending(Math.max(0, total - sent)).build();
    }

    /** 某类通知实际发到了哪些安排上。回集合而不是计数：名单要逐人标已发/未发 */
    private Set<Integer> sentScheduleIds(InterviewNotificationType type, Set<Integer> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return Set.of();
        }
        return notificationLogMapper.selectList(new LambdaQueryWrapper<InterviewNotificationLog>()
                        .eq(InterviewNotificationLog::getNotificationType, type.name())
                        .in(InterviewNotificationLog::getScheduleId, scheduleIds))
                .stream()
                .map(InterviewNotificationLog::getScheduleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 面试安排名单 + 三类通知逐人的发送状态。
     *
     * 姓名与学号从简历字段取，跟名单页同一套口径——user.username 多数是学号
     * 但早期账号是姓名拼音，拿它当学号会骗人。
     */
    private List<NotificationCenterDTO.ScheduleNoticeItem> buildScheduleNotices(
            Integer cycleId, List<InterviewSchedule> schedules,
            Set<Integer> eveSent, Set<Integer> daySent) {
        if (schedules.isEmpty()) {
            return List.of();
        }
        List<Integer> resumeIds = schedules.stream()
                .map(InterviewSchedule::getResumeId).filter(Objects::nonNull).distinct().toList();
        List<Integer> userIds = schedules.stream()
                .map(InterviewSchedule::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Integer, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));
        Map<Integer, String> deptNames = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getDeptId, Department::getDeptName, (a, b) -> a));
        Map<Integer, String> locations = schedules.stream()
                .map(InterviewSchedule::getSessionId).filter(Objects::nonNull).distinct().toList().isEmpty()
                ? Map.of()
                : sessionMapper.selectBatchIds(schedules.stream()
                        .map(InterviewSchedule::getSessionId).filter(Objects::nonNull).distinct().toList())
                        .stream().collect(Collectors.toMap(
                                InterviewSession::getSessionId, InterviewSession::getLocation, (a, b) -> a));
        Map<Integer, String> names = fieldValues(cycleId, resumeIds, List.of("姓名"));
        Map<Integer, String> studentIds = fieldValues(cycleId, resumeIds, STUDENT_ID_LABELS);

        List<NotificationCenterDTO.ScheduleNoticeItem> out = new ArrayList<>();
        for (InterviewSchedule sc : schedules) {
            User u = sc.getUserId() == null ? null : users.get(sc.getUserId());
            String name = names.get(sc.getResumeId());
            out.add(NotificationCenterDTO.ScheduleNoticeItem.builder()
                    .scheduleId(sc.getScheduleId())
                    .userId(sc.getUserId())
                    .name(name != null ? name : (u != null ? u.getName() : null))
                    .studentId(studentIds.get(sc.getResumeId()))
                    .interviewTime(sc.getInterviewTime())
                    .deptName(sc.getDeptId() == null ? null : deptNames.get(sc.getDeptId()))
                    .location(sc.getSessionId() == null ? null : locations.get(sc.getSessionId()))
                    .arranged(Integer.valueOf(1).equals(sc.getNotifStatus()))
                    .eve(eveSent.contains(sc.getScheduleId()))
                    .day(daySent.contains(sc.getScheduleId()))
                    .build());
        }
        out.sort(Comparator.comparing(
                NotificationCenterDTO.ScheduleNoticeItem::getInterviewTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    /** 批量取某组简历里某个字段的值；本届没有这个字段时返回空表 */
    private Map<Integer, String> fieldValues(Integer cycleId, List<Integer> resumeIds, List<String> labels) {
        if (resumeIds.isEmpty()) {
            return Map.of();
        }
        Integer fieldId = fieldIdOf(fieldDefinitionMapper.selectList(
                new LambdaQueryWrapper<ResumeFieldDefinition>()
                        .eq(ResumeFieldDefinition::getCycleId, cycleId)), labels);
        if (fieldId == null) {
            return Map.of();
        }
        Map<Integer, String> out = new HashMap<>();
        for (ResumeFieldValue v : fieldValueMapper.selectList(new LambdaQueryWrapper<ResumeFieldValue>()
                .in(ResumeFieldValue::getResumeId, resumeIds)
                .eq(ResumeFieldValue::getFieldId, fieldId))) {
            if (v.getFieldValue() != null && !v.getFieldValue().isBlank()) {
                out.put(v.getResumeId(), v.getFieldValue());
            }
        }
        return out;
    }

    /** 每份简历最近一次「初筛未通过」通知的时间。重发会留多条日志，取最新的 */
    private Map<Integer, LocalDateTime> latestSentByResume(List<Integer> resumeIds) {
        if (resumeIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, LocalDateTime> latest = new HashMap<>();
        for (InterviewNotificationLog logRow : notificationLogMapper.selectList(
                new LambdaQueryWrapper<InterviewNotificationLog>()
                        .eq(InterviewNotificationLog::getNotificationType,
                                InterviewNotificationType.RESUME_REJECTED.name())
                        .in(InterviewNotificationLog::getResumeId, resumeIds))) {
            LocalDateTime prev = latest.get(logRow.getResumeId());
            if (prev == null || (logRow.getSentAt() != null && logRow.getSentAt().isAfter(prev))) {
                latest.put(logRow.getResumeId(), logRow.getSentAt());
            }
        }
        return latest;
    }

    private List<NotificationCenterDTO.ScreenedOutItem> buildScreenedOut(
            Integer cycleId, List<Resume> rejected, Map<Integer, LocalDateTime> notified) {
        if (rejected.isEmpty()) {
            return List.of();
        }
        List<Integer> resumeIds = rejected.stream().map(Resume::getResumeId).toList();

        // 姓名/邮箱/学号在简历字段里，先按本届的字段定义找出对应 fieldId
        List<ResumeFieldDefinition> defs = fieldDefinitionMapper.selectList(
                new LambdaQueryWrapper<ResumeFieldDefinition>().eq(ResumeFieldDefinition::getCycleId, cycleId));
        Integer nameFieldId = fieldIdOf(defs, List.of("姓名"));
        Integer emailFieldId = fieldIdOf(defs, List.of("邮箱", "电子邮箱"));
        Integer studentFieldId = fieldIdOf(defs, STUDENT_ID_LABELS);

        Map<Integer, Map<Integer, String>> valuesByResume = new HashMap<>();
        List<Integer> wanted = new ArrayList<>();
        for (Integer id : new Integer[]{nameFieldId, emailFieldId, studentFieldId}) {
            if (id != null) wanted.add(id);
        }
        if (!wanted.isEmpty()) {
            for (ResumeFieldValue v : fieldValueMapper.selectList(new LambdaQueryWrapper<ResumeFieldValue>()
                    .in(ResumeFieldValue::getResumeId, resumeIds)
                    .in(ResumeFieldValue::getFieldId, wanted))) {
                valuesByResume.computeIfAbsent(v.getResumeId(), k -> new HashMap<>())
                        .put(v.getFieldId(), v.getFieldValue());
            }
        }

        List<Integer> userIds = rejected.stream()
                .map(Resume::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Integer, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));

        List<NotificationCenterDTO.ScreenedOutItem> out = new ArrayList<>();
        for (Resume r : rejected) {
            Map<Integer, String> vals = valuesByResume.getOrDefault(r.getResumeId(), Map.of());
            User u = r.getUserId() == null ? null : users.get(r.getUserId());
            String name = blankToNull(nameFieldId == null ? null : vals.get(nameFieldId));
            String email = blankToNull(emailFieldId == null ? null : vals.get(emailFieldId));
            out.add(NotificationCenterDTO.ScreenedOutItem.builder()
                    .resumeId(r.getResumeId())
                    .userId(r.getUserId())
                    .name(name != null ? name : (u != null ? u.getName() : null))
                    .studentId(blankToNull(studentFieldId == null ? null : vals.get(studentFieldId)))
                    .email(email != null ? email : (u != null ? u.getEmail() : null))
                    // 没署名就是没打过分：0 分和「没打分」必须区分，前者才是初筛未通过的依据
                    .resumeScore(r.getScoredBy() == null && r.getScoredAt() == null ? null : r.getResumeScore())
                    .notifiedAt(notified.get(r.getResumeId()))
                    .build());
        }
        out.sort(Comparator.comparing(
                (NotificationCenterDTO.ScreenedOutItem i) -> i.getNotifiedAt() != null)
                .thenComparing(NotificationCenterDTO.ScreenedOutItem::getResumeId));
        return out;
    }

    private Integer fieldIdOf(List<ResumeFieldDefinition> defs, List<String> labels) {
        return defs.stream()
                .filter(d -> labels.contains(d.getFieldLabel()))
                .map(ResumeFieldDefinition::getFieldId)
                .findFirst()
                .orElse(null);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
