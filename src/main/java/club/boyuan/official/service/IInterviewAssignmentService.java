package club.boyuan.official.service;

import club.boyuan.official.dto.InterviewAssignmentResultDTO;

import java.util.Map;

/**
 * 面试时间分配服务接口
 */
public interface IInterviewAssignmentService {
    
    /**
     * 为指定招募周期分配面试时间
     * 
     * @param cycleId 招募周期ID
     * @return 面试时间分配结果
     */
    InterviewAssignmentResultDTO assignInterviews(Integer cycleId);
}