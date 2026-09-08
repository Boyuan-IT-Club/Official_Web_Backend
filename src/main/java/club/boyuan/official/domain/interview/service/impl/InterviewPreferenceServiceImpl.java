package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.InterviewPreferenceDTO;
import club.boyuan.official.domain.interview.dto.UpdateAttendanceRequestDTO;
import club.boyuan.official.domain.interview.dto.InterviewTimeSlotDTO;
import club.boyuan.official.domain.interview.dto.SubmitInterviewPreferenceRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewPreferenceService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewPreference;
import club.boyuan.official.persistence.entity.InterviewPreferenceTime;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewPreferenceMapper;
import club.boyuan.official.persistence.mapper.InterviewPreferenceTimeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学生面试志愿服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPreferenceServiceImpl extends ServiceImpl<InterviewPreferenceMapper, InterviewPreference>
        implements IInterviewPreferenceService {

    private static final int RESUME_STATUS_SUBMITTED = 2;
    private static final int TIME_SLOT_STATUS_OPEN = 1;

    private final IResumeService resumeService;
    private final IInterviewTimeSlotService interviewTimeSlotService;
    private final InterviewPreferenceTimeMapper preferenceTimeMapper;
    private final DepartmentMapper departmentMapper;
    private final InterviewScheduleMapper interviewScheduleMapper;
    private final club.boyuan.official.persistence.mapper.ResumeFieldDefinitionMapper resumeFieldDefinitionMapper;
    private final club.boyuan.official.persistence.mapper.ResumeFieldValueMapper resumeFieldValueMapper;

    @Override
    @Transactional
    public InterviewPreferenceDTO submitPreference(Integer userId, SubmitInterviewPreferenceRequestDTO request) {
        Resume resume = requireSubmittedResume(userId, request.getCycleId());

        // 面试一旦安排（status=1）就锁定意向：算法已按旧志愿排完场次，
        // 此时再改志愿/时间窗不会生效，只会让学生以为改了、面试官按旧安排等人。
        // 正确的调整通道是改期申请（InterviewRescheduleController）。
        // 已取消（status=2）不拦 —— 取消后重排前学生应能改意向。
        boolean scheduled = interviewScheduleMapper.exists(new LambdaQueryWrapper<InterviewSchedule>()
                .eq(InterviewSchedule::getResumeId, resume.getResumeId())
                .eq(InterviewSchedule::getCycleId, request.getCycleId())
                .eq(InterviewSchedule::getStatus, 1));
        if (scheduled) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_PREFERENCE_LOCKED_BY_SCHEDULE);
        }

        Integer firstDeptId = request.getFirstDeptId();
        Integer secondDeptId = request.getSecondDeptId();
        if (firstDeptId == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_PREFERENCE_DEPT_REQUIRED);
        }
        if (secondDeptId != null && secondDeptId.equals(firstDeptId)) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_PREFERENCE_DEPT_DUPLICATE);
        }
        if (departmentMapper.selectById(firstDeptId) == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND);
        }
        if (secondDeptId != null && departmentMapper.selectById(secondDeptId) == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND);
        }

        List<Integer> timeSlotIds = request.getTimeSlotIds() == null ? new ArrayList<>()
                : request.getTimeSlotIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (timeSlotIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_PREFERENCE_TIME_REQUIRED);
        }
        List<InterviewTimeSlot> timeSlots = interviewTimeSlotService.listByIds(timeSlotIds);
        if (timeSlots.size() != timeSlotIds.size()) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_PREFERENCE_TIME_INVALID);
        }
        for (InterviewTimeSlot ts : timeSlots) {
            if (!Objects.equals(ts.getCycleId(), request.getCycleId())
                    || !Integer.valueOf(TIME_SLOT_STATUS_OPEN).equals(ts.getStatus())) {
                throw new BusinessException(BusinessExceptionEnum.INTERVIEW_PREFERENCE_TIME_INVALID);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        InterviewPreference preference = getByResumeId(resume.getResumeId());
        if (preference == null) {
            preference = new InterviewPreference()
                    .setResumeId(resume.getResumeId())
                    .setCycleId(request.getCycleId());
        }
        preference.setFirstDeptId(firstDeptId)
                .setSecondDeptId(secondDeptId)
                .setSubmittedAt(now);
        saveOrUpdate(preference);

        preferenceTimeMapper.delete(new LambdaQueryWrapper<InterviewPreferenceTime>()
                .eq(InterviewPreferenceTime::getResumeId, resume.getResumeId()));
        for (Integer timeSlotId : timeSlotIds) {
            preferenceTimeMapper.insert(new InterviewPreferenceTime()
                    .setResumeId(resume.getResumeId())
                    .setTimeSlotId(timeSlotId));
        }

        log.info("学生提交面试志愿成功，userId={}, resumeId={}, first={}, second={}, timeSlots={}",
                userId, resume.getResumeId(), firstDeptId, secondDeptId, timeSlotIds);
        return buildDTO(preference, timeSlots);
    }

    @Override
    public InterviewPreferenceDTO getMyPreference(Integer userId, Integer cycleId) {
        Resume resume = resumeService.getResumeByUserIdAndCycleId(userId, cycleId);
        if (resume == null) {
            return null;
        }
        InterviewPreference preference = getByResumeId(resume.getResumeId());
        if (preference == null) {
            return null;
        }
        List<Integer> timeSlotIds = preferenceTimeMapper.selectList(
                        new LambdaQueryWrapper<InterviewPreferenceTime>()
                                .eq(InterviewPreferenceTime::getResumeId, resume.getResumeId()))
                .stream().map(InterviewPreferenceTime::getTimeSlotId).collect(Collectors.toList());
        List<InterviewTimeSlot> timeSlots = timeSlotIds.isEmpty()
                ? new ArrayList<>() : interviewTimeSlotService.listByIds(timeSlotIds);
        return buildDTO(preference, timeSlots);
    }

    private InterviewPreference getByResumeId(Integer resumeId) {
        return getOne(new LambdaQueryWrapper<InterviewPreference>()
                .eq(InterviewPreference::getResumeId, resumeId), false);
    }

    private InterviewPreferenceDTO buildDTO(InterviewPreference preference, List<InterviewTimeSlot> timeSlots) {
        InterviewPreferenceDTO dto = new InterviewPreferenceDTO();
        dto.setPreferenceId(preference.getPreferenceId());
        dto.setResumeId(preference.getResumeId());
        dto.setCycleId(preference.getCycleId());
        dto.setFirstDeptId(preference.getFirstDeptId());
        dto.setFirstDeptName(deptName(preference.getFirstDeptId()));
        dto.setSecondDeptId(preference.getSecondDeptId());
        dto.setSecondDeptName(deptName(preference.getSecondDeptId()));
        dto.setSubmittedAt(preference.getSubmittedAt());
        dto.setAcceptedTimeSlots(timeSlots.stream().map(this::toTimeSlotDTO).collect(Collectors.toList()));
        return dto;
    }

    private InterviewTimeSlotDTO toTimeSlotDTO(InterviewTimeSlot ts) {
        InterviewTimeSlotDTO dto = new InterviewTimeSlotDTO();
        dto.setTimeSlotId(ts.getTimeSlotId());
        dto.setCycleId(ts.getCycleId());
        dto.setSlotName(ts.getSlotName());
        dto.setInterviewDate(ts.getInterviewDate());
        dto.setStartTime(ts.getStartTime());
        dto.setEndTime(ts.getEndTime());
        dto.setStatus(ts.getStatus());
        return dto;
    }

    private String deptName(Integer deptId) {
        if (deptId == null) {
            return null;
        }
        Department dept = departmentMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    /**
     * 更新「能否到线下参加面试」。
     *
     * 为什么不走 /api/resumes/.../field-values：那条路要求投递期开放，而改
     * 线上/线下恰恰多发生在投递期结束、分配已跑之后（Publish 页还承诺过
     * 「情况有变可随时改回」）。这里对齐志愿修改的锁定规则——只在已排上
     * 生效场次时拒绝（改期走 InterviewRescheduleController），其余时间放行。
     *
     * 只合并 canAttend / customTime 两个键，志愿部门（first/second）原样保留。
     */
    @Override
    @Transactional
    public java.util.Map<String, Object> updateAttendance(Integer userId, UpdateAttendanceRequestDTO request) {
        Resume resume = requireSubmittedResume(userId, request.getCycleId());

        boolean scheduled = interviewScheduleMapper.exists(new LambdaQueryWrapper<InterviewSchedule>()
                .eq(InterviewSchedule::getResumeId, resume.getResumeId())
                .eq(InterviewSchedule::getCycleId, request.getCycleId())
                .eq(InterviewSchedule::getStatus, 1));
        if (scheduled) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_PREFERENCE_LOCKED_BY_SCHEDULE);
        }

        club.boyuan.official.persistence.entity.ResumeFieldDefinition def =
                resumeFieldDefinitionMapper.selectOne(
                        new LambdaQueryWrapper<club.boyuan.official.persistence.entity.ResumeFieldDefinition>()
                                .eq(club.boyuan.official.persistence.entity.ResumeFieldDefinition::getCycleId,
                                        request.getCycleId())
                                .eq(club.boyuan.official.persistence.entity.ResumeFieldDefinition::getFieldKey,
                                        "expected_interview_time")
                                .last("LIMIT 1"));
        if (def == null) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "本周期未配置面试意向字段，请联系管理员");
        }

        club.boyuan.official.persistence.entity.ResumeFieldValue value =
                resumeFieldValueMapper.selectOne(
                        new LambdaQueryWrapper<club.boyuan.official.persistence.entity.ResumeFieldValue>()
                                .eq(club.boyuan.official.persistence.entity.ResumeFieldValue::getResumeId,
                                        resume.getResumeId())
                                .eq(club.boyuan.official.persistence.entity.ResumeFieldValue::getFieldId,
                                        def.getFieldId())
                                .last("LIMIT 1"));

        // 在既有 JSON 上合并，脏数据当成空对象重建（不能因为一行坏 JSON 卡死学生）
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode node;
        try {
            com.fasterxml.jackson.databind.JsonNode parsed =
                    (value != null && org.springframework.util.StringUtils.hasText(value.getFieldValue()))
                            ? om.readTree(value.getFieldValue()) : null;
            node = (parsed instanceof com.fasterxml.jackson.databind.node.ObjectNode)
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) parsed
                    : om.createObjectNode();
        } catch (Exception e) {
            node = om.createObjectNode();
        }
        boolean offline = Boolean.TRUE.equals(request.getCanAttendOffline());
        node.put("canAttend", offline ? "yes" : "no");
        // 改回线下时清掉旧说明，免得「待约线上面试」名单里残留过期原因
        node.put("customTime", offline ? "" : (request.getCustomTime() == null ? "" : request.getCustomTime()));

        String json = node.toString();
        LocalDateTime now = LocalDateTime.now();
        if (value == null) {
            club.boyuan.official.persistence.entity.ResumeFieldValue fresh =
                    new club.boyuan.official.persistence.entity.ResumeFieldValue();
            fresh.setResumeId(resume.getResumeId());
            fresh.setFieldId(def.getFieldId());
            fresh.setFieldValue(json);
            fresh.setCreatedAt(now);
            fresh.setUpdatedAt(now);
            resumeFieldValueMapper.insert(fresh);
        } else {
            value.setFieldValue(json);
            value.setUpdatedAt(now);
            resumeFieldValueMapper.updateById(value);
        }
        log.info("学生更新线下参加意向，userId={}, resumeId={}, canAttendOffline={}",
                userId, resume.getResumeId(), offline);
        return java.util.Map.of("canAttendOffline", offline,
                "customTime", node.path("customTime").asText(""));
    }

    private Resume requireSubmittedResume(Integer userId, Integer cycleId) {
        Resume resume = resumeService.getResumeByUserIdAndCycleId(userId, cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        if (resume.getStatus() == null || resume.getStatus() < RESUME_STATUS_SUBMITTED) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_SUBMITTED_FOR_BOOKING);
        }
        return resume;
    }
}
