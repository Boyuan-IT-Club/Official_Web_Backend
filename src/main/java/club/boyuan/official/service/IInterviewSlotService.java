package club.boyuan.official.service;

import club.boyuan.official.dto.CreateInterviewSlotRequestDTO;
import club.boyuan.official.dto.GetInterviewSlotListResponseDTO;
import club.boyuan.official.dto.UpdateInterviewSlotDTO;
import club.boyuan.official.entity.InterviewSlot;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.validation.Valid;

import java.util.List;

/**
 * <p>
 * 面试时段配置表 服务类
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
public interface IInterviewSlotService extends IService<InterviewSlot> {

    InterviewSlot createInterviewSlot(CreateInterviewSlotRequestDTO requestDTO);

    InterviewSlot updateInterviewSlot(Integer slotId, UpdateInterviewSlotDTO requestDTO);

    GetInterviewSlotListResponseDTO listInterviewSlots(Integer cycleId, String interviewDate, String startTime, String location, Integer status, Integer interviewType, Integer page, Integer size);

    /**
     * 根据招募周期ID获取可用的面试时间槽
     * @param cycleId 招募周期ID
     * @return 可用的面试时间槽列表
     */
    List<InterviewSlot> getAvailableSlotsByCycleId(Integer cycleId);

    /**
     * 根据招募周期ID获取所有面试时间槽（包括已占用的）
     * @param cycleId 招募周期ID
     * @return 所有面试时间槽列表
     */
    List<InterviewSlot> getAllSlotsByCycleId(Integer cycleId);

}
