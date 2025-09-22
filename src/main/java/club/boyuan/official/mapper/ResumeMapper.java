package club.boyuan.official.mapper;

import club.boyuan.official.entity.Resume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResumeMapper {
    
    /**
     * 根据用户ID和招聘年份ID查询简历
     * @param userId 用户ID
     * @param cycleId 招聘年份ID
     * @return 简历
     */
    Resume findByUserIdAndCycleId(@Param("userId") Integer userId, @Param("cycleId") Integer cycleId);
    
    /**
     * 根据简历ID查询简历
     * @param resumeId 简历ID
     * @return 简历
     */
    Resume findById(Integer resumeId);
    
    /**
     * 根据用户ID查询简历列表
     * @param userId 用户ID
     * @return 简历列表
     */
    List<Resume> findByUserId(Integer userId);
    
    /**
     * 根据招聘周期ID查询简历列表
     * @param cycleId 招聘周期ID
     * @return 简历列表
     */
    List<Resume> findByCycleId(@Param("cycleId") Integer cycleId);
    
    /**
     * 插入简历
     * @param resume 简历实体
     * @return 影响行数
     */
    int insert(Resume resume);
    
    /**
     * 更新简历
     * @param resume 简历实体
     * @return 影响行数
     */
    int update(Resume resume);
    
    /**
     * 删除简历
     * @param resumeId 简历ID
     * @return 影响行数
     */
    int deleteById(Integer resumeId);
    
    /**
     * 根据用户ID删除简历
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(Integer userId);
    
    /**
     * 多条件查询简历列表
     * @param name 姓名（可选）
     * @param major 专业（可选）
     * @param cycleId 年份ID（可选）
     * @param status 简历状态（可选）
     * @return 简历列表
     */
    List<Resume> queryResumes(@Param("name") String name, @Param("major") String major, @Param("cycleId") Integer cycleId, @Param("status") Integer status);
    
    /**
     * 多条件查询简历列表（分页）
     * @param name 姓名（可选）
     * @param major 专业（可选）
     * @param cycleId 年份ID（可选）
     * @param status 简历状态（可选）
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 简历列表
     */
    List<Resume> queryResumesWithPagination(@Param("name") String name, @Param("major") String major, @Param("cycleId") Integer cycleId, @Param("status") Integer status, @Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 统计多条件查询简历数量
     * @param name 姓名（可选）
     * @param major 专业（可选）
     * @param cycleId 年份ID（可选）
     * @param status 简历状态（可选）
     * @return 简历数量
     */
    int countResumes(@Param("name") String name, @Param("major") String major, @Param("cycleId") Integer cycleId, @Param("status") Integer status);
}