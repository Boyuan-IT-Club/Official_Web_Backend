package club.boyuan.official.mapper;

import club.boyuan.official.entity.RecruitmentCycle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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
    
    /**
     * 批量删除招募周期
     * @param cycleIds 招募周期ID列表
     * @return 影响行数
     */
    int batchDelete(@Param("cycleIds") List<Integer> cycleIds);
    
    /**
     * 批量更新招募周期
     * @param recruitmentCycles 招募周期列表
     * @return 影响行数
     */
    int batchUpdate(@Param("recruitmentCycles") List<RecruitmentCycle> recruitmentCycles);
    
    /**
     * 查询所有招募周期（支持分页和排序）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param sortBy 排序字段
     * @param sortOrder 排序顺序
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findAllWithPaginationAndSorting(@Param("offset") int offset, 
                                                           @Param("limit") int limit, 
                                                           @Param("sortBy") String sortBy, 
                                                           @Param("sortOrder") String sortOrder);
    
    /**
     * 根据条件查询招募周期（支持分页和排序）
     * @param cycleName 招募周期名称
     * @param academicYear 学年
     * @param status 状态
     * @param isActive 是否启用
     * @param offset 偏移量
     * @param limit 限制数量
     * @param sortBy 排序字段
     * @param sortOrder 排序顺序
     * @return 招募周期列表
     */
    List<RecruitmentCycle> findByConditions(@Param("cycleName") String cycleName,
                                            @Param("academicYear") String academicYear,
                                            @Param("status") Integer status,
                                            @Param("isActive") Integer isActive,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit,
                                            @Param("sortBy") String sortBy,
                                            @Param("sortOrder") String sortOrder);
    
    /**
     * 统计符合条件的招募周期数量
     * @param cycleName 招募周期名称
     * @param academicYear 学年
     * @param status 状态
     * @param isActive 是否启用
     * @return 数量
     */
    long countByConditions(@Param("cycleName") String cycleName,
                           @Param("academicYear") String academicYear,
                           @Param("status") Integer status,
                           @Param("isActive") Integer isActive);
    
    /**
     * 根据当前日期更新招募周期状态
     * @param currentDate 当前日期
     * @return 影响行数
     */
    int updateStatusBasedOnDate(@Param("currentDate") LocalDate currentDate);
}