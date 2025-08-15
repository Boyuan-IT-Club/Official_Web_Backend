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
}