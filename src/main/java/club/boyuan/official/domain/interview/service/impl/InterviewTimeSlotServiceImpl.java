package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.CreateInterviewTimeSlotRequestDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeSlotRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.InterviewTimeSlotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 面试时间窗服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewTimeSlotServiceImpl extends ServiceImpl<InterviewTimeSlotMapper, InterviewTimeSlot>
        implements IInterviewTimeSlotService {

    private static final int STATUS_OPEN = 1;

    private final IRecruitmentCycleService recruitmentCycleService;
    private final InterviewSessionMapper interviewSessionMapper;

    @Override
    public InterviewTimeSlot createTimeSlot(CreateInterviewTimeSlotRequestDTO request) {
        validateCycleExists(request.getCycleId());
        InterviewTimeSlot slot = new InterviewTimeSlot()
                .setCycleId(request.getCycleId())
                .setSlotName(request.getSlotName())
                .setInterviewDate(request.getInterviewDate())
                .setStartTime(request.getStartTime())
                .setEndTime(request.getEndTime())
                .setStatus(STATUS_OPEN);
        save(slot);
        log.info("创建面试时间窗成功，timeSlotId={}, cycleId={}, name={}",
                slot.getTimeSlotId(), slot.getCycleId(), slot.getSlotName());
        return slot;
    }

    @Override
    public InterviewTimeSlot updateTimeSlot(Integer timeSlotId, UpdateInterviewTimeSlotRequestDTO request) {
        InterviewTimeSlot slot = requireTimeSlot(timeSlotId);
        if (request.getSlotName() != null) {
            slot.setSlotName(request.getSlotName());
        }
        if (request.getInterviewDate() != null) {
            slot.setInterviewDate(request.getInterviewDate());
        }
        if (request.getStartTime() != null) {
            slot.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            slot.setEndTime(request.getEndTime());
        }
        if (request.getStatus() != null) {
            slot.setStatus(request.getStatus());
        }
        updateById(slot);
        log.info("更新面试时间窗成功，timeSlotId={}", timeSlotId);
        return slot;
    }

    @Override
    public void deleteTimeSlot(Integer timeSlotId) {
        requireTimeSlot(timeSlotId);
        Long sessionCount = interviewSessionMapper.selectCount(
                new LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewSession>()
                        .eq(club.boyuan.official.persistence.entity.InterviewSession::getTimeSlotId, timeSlotId));
        if (sessionCount != null && sessionCount > 0) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_TIME_SLOT_HAS_SESSION);
        }
        removeById(timeSlotId);
        log.info("删除面试时间窗成功，timeSlotId={}", timeSlotId);
    }

    @Override
    public List<InterviewTimeSlot> listByCycle(Integer cycleId, boolean onlyOpen) {
        LambdaQueryWrapper<InterviewTimeSlot> wrapper = new LambdaQueryWrapper<InterviewTimeSlot>()
                .eq(InterviewTimeSlot::getCycleId, cycleId)
                .orderByAsc(InterviewTimeSlot::getInterviewDate)
                .orderByAsc(InterviewTimeSlot::getStartTime);
        if (onlyOpen) {
            wrapper.eq(InterviewTimeSlot::getStatus, STATUS_OPEN);
        }
        return list(wrapper);
    }

    private InterviewTimeSlot requireTimeSlot(Integer timeSlotId) {
        InterviewTimeSlot slot = getById(timeSlotId);
        if (slot == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_TIME_SLOT_NOT_FOUND);
        }
        return slot;
    }

    private void validateCycleExists(Integer cycleId) {
        RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
        if (cycle == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
    }
}
