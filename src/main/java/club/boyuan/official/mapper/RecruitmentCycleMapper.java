package club.boyuan.official.mapper;

import club.boyuan.official.entity.RecruitmentCycle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 招募周期Mapper接口
 */
@Mapper
public interface RecruitmentCycleMapper {
    
    /**
     * 插入招募周期
     * @param recruitmentCycle 招募周期实体
     * @return 影响行数
     */
    int insert(RecruitmentCycle recruitmentCycle);
    
    /**
     * 根据ID更新招募周期
     * @param recruitmentCycle 招募周期实体
     * @return 影响行数
     */
    int update(RecruitmentCycle recruitmentCycle);
    
    /**
     * 根据ID删除招募周期
     * @param cycleId 招募周期ID
     * @return 影响行数
     */
    int deleteById(Integer cycleId);
    
    /**
     * 根据ID查询招募周期
     * @param cycleId 招募周期ID
     * @return 招募周期实体
     */
    RecruitmentCycle findById(Integer cycleId);
    
    /**
     * 查询所有招募周期
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findAll();
    
    /**
     * 根据状态查询招募周期
     * @param status 状态
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findByStatus(Integer status);
    
    /**
     * 根据是否启用查询招募周期
     * @param isActive 是否启用
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findByIsActive(Integer isActive);
    
    /**
     * 根据学年查询招募周期
     * @param academicYear 学年
     * @return 招募周期实体
     */
    RecruitmentCycle findByAcademicYear(String academicYear);
}