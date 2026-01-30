package club.boyuan.official.service.impl;

import club.boyuan.official.dto.CreateInterviewSlotRequestDTO;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.mapper.InterviewSlotMapper;
import club.boyuan.official.service.IInterviewSlotService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 面试时段配置表 服务实现类
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Service
public class InterviewSlotServiceImpl extends ServiceImpl<InterviewSlotMapper, InterviewSlot> implements IInterviewSlotService {

    @Override
    public InterviewSlot createInterviewSlot(CreateInterviewSlotRequestDTO requestDTO) {
        //验证时段的合理性
        if(requestDTO.getStartTime().isAfter(requestDTO.getEndTime())){
            throw new RuntimeException("面试时段开始时间不能晚于结束时间");
        }
        InterviewSlot interviewSlot = new InterviewSlot()
                .setCycleId(requestDTO.getCycleId())
                .setInterviewDate(requestDTO.getInterviewDate())
                .setStartTime(requestDTO.getStartTime())
                .setEndTime(requestDTO.getEndTime())
                .setLocation(requestDTO.getLocation())
                .setInterviewType(requestDTO.getInterviewType())
                .setMeetingLink(requestDTO.getMeetingLink())
                .setMaxCapacity(requestDTO.getMaxCapacity())
                .setFeishuTableUrl(requestDTO.getFeishuTableUrl())
                .setStatus(1) //初始状态可用
                .setCurrentOccupied(0); //占用0
        this.save(interviewSlot);
        return interviewSlot;
    }
}
