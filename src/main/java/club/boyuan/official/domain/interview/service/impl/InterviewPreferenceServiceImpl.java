package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.InterviewPreferenceDTO;
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

    @Override
    @Transactional
    public InterviewPreferenceDTO submitPreference(Integer userId, SubmitInterviewPreferenceRequestDTO request) {
        Resume resume = requireSubmittedResume(userId, request.getCycleId());

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
