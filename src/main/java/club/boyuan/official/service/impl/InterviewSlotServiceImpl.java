package club.boyuan.official.service.impl;

import club.boyuan.official.dto.CreateInterviewSlotRequestDTO;
import club.boyuan.official.dto.GetInterviewSlotListResponseDTO;
import club.boyuan.official.dto.UpdateInterviewSlotDTO;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.mapper.InterviewSlotMapper;
import club.boyuan.official.service.IInterviewSlotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @Override
    public InterviewSlot updateInterviewSlot(Integer slotId, UpdateInterviewSlotDTO requestDTO) {

        //使用LambdaUpdateWrapper实现更新
        LambdaUpdateWrapper<InterviewSlot> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper
                .eq(InterviewSlot::getSlotId, slotId)
                .set(requestDTO.getInterviewDate()!= null, InterviewSlot::getInterviewDate, requestDTO.getInterviewDate())
                .set(requestDTO.getStartTime()!= null, InterviewSlot::getStartTime, requestDTO.getStartTime())
                .set(requestDTO.getEndTime()!= null, InterviewSlot::getEndTime, requestDTO.getEndTime())
                .set(requestDTO.getLocation()!= null, InterviewSlot::getLocation, requestDTO.getLocation())
                .set(requestDTO.getInterviewType()!= null, InterviewSlot::getInterviewType, requestDTO.getInterviewType())
                .set(requestDTO.getMeetingLink()!= null, InterviewSlot::getMeetingLink, requestDTO.getMeetingLink())
                .set(requestDTO.getMaxCapacity()!= null, InterviewSlot::getMaxCapacity, requestDTO.getMaxCapacity())
                .set(requestDTO.getFeishuTableUrl()!= null, InterviewSlot::getFeishuTableUrl, requestDTO.getFeishuTableUrl());

        this.update(updateWrapper);
        return this.getById(slotId);
    }

    @Override
    public GetInterviewSlotListResponseDTO listInterviewSlots(Integer cycleId, String interviewDate, String startTime, String location, Integer status, Integer interviewType, Integer page, Integer size) {
        //构造查询条件
        LambdaQueryWrapper<InterviewSlot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(InterviewSlot::getCycleId, cycleId)
                .eq(interviewDate != null, InterviewSlot::getInterviewDate, interviewDate)
                .eq(startTime != null, InterviewSlot::getStartTime, startTime)
                .eq(location != null, InterviewSlot::getLocation, location)
                .eq(status != null, InterviewSlot::getStatus, status)
                .eq(interviewType != null, InterviewSlot::getInterviewType, interviewType);
        //创建分页对象
        Page<InterviewSlot> pageInfo = new Page<>(page, size);
        Page<InterviewSlot> resultPage = this.page(pageInfo, queryWrapper);

        //构建响应对象
        GetInterviewSlotListResponseDTO responseDTO = new GetInterviewSlotListResponseDTO();
        responseDTO.setTotal(resultPage.getTotal());
        responseDTO.setList(resultPage.getRecords());
        return responseDTO;

    }
}
