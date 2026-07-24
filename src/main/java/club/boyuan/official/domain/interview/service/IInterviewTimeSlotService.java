package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.CreateInterviewTimeSlotRequestDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeSlotRequestDTO;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 面试时间窗服务
 */
public interface IInterviewTimeSlotService extends IService<InterviewTimeSlot> {

    InterviewTimeSlot createTimeSlot(CreateInterviewTimeSlotRequestDTO request);

    InterviewTimeSlot updateTimeSlot(Integer timeSlotId, UpdateInterviewTimeSlotRequestDTO request);

    void deleteTimeSlot(Integer timeSlotId);

    /**
     * 列出某周期的时间窗。
     *
     * @param onlyOpen true 时仅返回状态为"可选"的时间窗（学生视角）
     */
    List<InterviewTimeSlot> listByCycle(Integer cycleId, boolean onlyOpen);
}
