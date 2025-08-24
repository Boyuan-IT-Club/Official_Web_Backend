package club.boyuan.official.service;

import club.boyuan.official.entity.RecruitmentCycle;

import java.util.List;

/**
 * 招募周期服务接口
 */
public interface IRecruitmentCycleService {
    
    /**
     * 创建招募周期
     * @param recruitmentCycle 招募周期实体
     * @return 创建后的招募周期
     */
    RecruitmentCycle createRecruitmentCycle(RecruitmentCycle recruitmentCycle);
    
    /**
     * 更新招募周期
     * @param recruitmentCycle 招募周期实体
     * @return 更新后的招募周期
     */
    RecruitmentCycle updateRecruitmentCycle(RecruitmentCycle recruitmentCycle);
    
    /**
     * 删除招募周期
     * @param cycleId 招募周期ID
     */
    void deleteRecruitmentCycle(Integer cycleId);
    
    /**
     * 根据ID获取招募周期
     * @param cycleId 招募周期ID
     * @return 招募周期实体
     */
    RecruitmentCycle getRecruitmentCycleById(Integer cycleId);
    
    /**
     * 获取所有招募周期
     * @return 招募周期列表
     */
    List<RecruitmentCycle> getAllRecruitmentCycles();
    
    /**
     * 根据状态获取招募周期
     * @param status 状态
     * @return 招募周期列表
     */
    List<RecruitmentCycle> getRecruitmentCyclesByStatus(Integer status);
    
    /**
     * 根据是否启用获取招募周期
     * @param isActive 是否启用
     * @return 招募周期列表
     */
    List<RecruitmentCycle> getRecruitmentCyclesByIsActive(Integer isActive);
    
    /**
     * 根据学年获取招募周期
     * @param academicYear 学年
     * @return 招募周期实体
     */
    RecruitmentCycle getRecruitmentCycleByAcademicYear(String academicYear);
}