package club.boyuan.official.mapper;

import club.boyuan.official.entity.ResumeFieldDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResumeFieldDefinitionMapper {
    
    /**
     * 根据招聘年份ID查询字段定义列表
     * @param cycleId 招聘年份ID
     * @return 字段定义列表
     */
    List<ResumeFieldDefinition> findByCycleId(Integer cycleId);
    
    /**
     * 根据字段ID查询字段定义
     * @param fieldId 字段ID
     * @return 字段定义
     */
    ResumeFieldDefinition findById(Integer fieldId);
    
    /**
     * 插入字段定义
     * @param fieldDefinition 字段定义实体
     * @return 影响行数
     */
    int insert(ResumeFieldDefinition fieldDefinition);
    
    /**
     * 更新字段定义
     * @param fieldDefinition 字段定义实体
     * @return 影响行数
     */
    int update(ResumeFieldDefinition fieldDefinition);
    
    
    /**
     * 删除字段定义
     * @param fieldId 字段ID
     * @return 影响行数
     */
    int deleteById(Integer fieldId);
}