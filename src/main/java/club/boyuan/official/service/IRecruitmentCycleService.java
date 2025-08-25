package club.boyuan.official.service;

import club.boyuan.official.dto.PageResultDTO;
import club.boyuan.official.entity.RecruitmentCycle;

import java.time.LocalDate;
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
    
    /**
     * 批量删除招募周期
     * @param cycleIds 招募周期ID列表
     */
    void deleteRecruitmentCycles(List<Integer> cycleIds);
    
    /**
     * 根据当前时间自动更新招募周期状态
     * @param currentDate 当前日期
     */
    void updateRecruitmentCycleStatusesBasedOnDate(LocalDate currentDate);
    
    /**
     * 批量更新招募周期
     * @param recruitmentCycles 招募周期列表
     */
    void updateRecruitmentCycles(List<RecruitmentCycle> recruitmentCycles);
    
    /**
     * 分页获取所有招募周期
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sortBy 排序字段
     * @param sortOrder 排序顺序（ASC/DESC）
     * @return 分页结果
     */
    PageResultDTO<RecruitmentCycle> getAllRecruitmentCyclesWithPagination(int page, int size, String sortBy, String sortOrder);
    
    /**
     * 根据条件分页查询招募周期
     * @param cycleName 招募周期名称
     * @param academicYear 学年
     * @param status 状态
     * @param isActive 是否启用
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sortBy 排序字段
     * @param sortOrder 排序顺序（ASC/DESC）
     * @return 分页结果
     */
    PageResultDTO<RecruitmentCycle> getRecruitmentCyclesByConditions(String cycleName, String academicYear, 
                                                                     Integer status, Integer isActive,
                                                                     int page, int size, String sortBy, String sortOrder);
}