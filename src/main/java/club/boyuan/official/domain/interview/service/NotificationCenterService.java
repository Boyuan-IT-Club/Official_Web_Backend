package club.boyuan.official.domain.interview.service;

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

        return NotificationCenterDTO.builder()
                .resumeRejected(bucket(screenedOut.size(), rejectedSent))
                .interviewArranged(bucket(schedules.size(), arrangedSent))
                .eveReminder(bucket(schedules.size(),
                        countSentSchedules(InterviewNotificationType.EVE_REMINDER, scheduleIds)))
                .dayReminder(bucket(schedules.size(),
                        countSentSchedules(InterviewNotificationType.DAY_REMINDER, scheduleIds)))
                .result(bucket(results.size(), resultSent))
                .screenedOut(screenedOut)
                .build();
    }

    private NotificationCenterDTO.Bucket bucket(long total, long sent) {
        return NotificationCenterDTO.Bucket.builder()
                .total(total).sent(sent).pending(Math.max(0, total - sent)).build();
    }

    /** 某类通知在本届安排里覆盖到多少人 */
    private long countSentSchedules(InterviewNotificationType type, Set<Integer> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return 0;
        }
        return notificationLogMapper.selectList(new LambdaQueryWrapper<InterviewNotificationLog>()
                        .eq(InterviewNotificationLog::getNotificationType, type.name())
                        .in(InterviewNotificationLog::getScheduleId, scheduleIds))
                .stream()
                .map(InterviewNotificationLog::getScheduleId)
                .distinct()
                .count();
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
