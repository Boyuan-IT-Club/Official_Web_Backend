package club.boyuan.official.mapper;

import club.boyuan.official.entity.ResumeFieldValue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResumeFieldValueMapper extends BaseMapper<ResumeFieldValue> {
    
    /**
     * 根据简历ID查询字段值列表
     * @param resumeId 简历ID
     * @return 字段值列表
     */
    List<ResumeFieldValue> findByResumeId(Integer resumeId);
    
    /**
     * 根据简历ID和字段ID查询字段值
     * @param resumeId 简历ID
     * @param fieldId 字段ID
     * @return 字段值
     */
    ResumeFieldValue findByResumeIdAndFieldId(@Param("resumeId") Integer resumeId, @Param("fieldId") Integer fieldId);
    
    /**
     * 批量插入字段值
     * @param fieldValues 字段值列表
     * @return 影响行数
     */
    int batchInsert(@Param("fieldValues") List<ResumeFieldValue> fieldValues);
    
    /**
     * 更新字段值
     * @param fieldValue 字段值实体
     * @return 影响行数
     */
    int update(ResumeFieldValue fieldValue);
    
    /**
     * 批量更新字段值
     * @param fieldValues 字段值列表
     * @return 影响行数
     */
    int batchUpdate(@Param("fieldValues") List<ResumeFieldValue> fieldValues);
    
    /**
     * 根据简历ID删除字段值
     * @param resumeId 简历ID
     * @return 影响行数
     */
    int deleteByResumeId(Integer resumeId);
    
    /**
     * 根据字段ID删除字段值
     * @param fieldId 字段ID
     * @return 影响行数
     */
    int deleteByFieldId(Integer fieldId);
}