package club.boyuan.official.service;

import club.boyuan.official.dto.AutoAssignInterviewResponseDTO;
import club.boyuan.official.entity.InterviewSchedule;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 面试安排表 服务类
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
public interface IInterviewScheduleService extends IService<InterviewSchedule> {

    /**
     * 一键分配面试成员面试时间地点（按招募周期）
     * 
     * @param cycleId  分配请求参数
     * @return 分配结果
     */
    AutoAssignInterviewResponseDTO autoAssignInterviews(Integer cycleId);
}
