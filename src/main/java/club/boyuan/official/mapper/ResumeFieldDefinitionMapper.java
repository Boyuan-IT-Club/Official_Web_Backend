package club.boyuan.official.mapper;

import club.boyuan.official.entity.ResumeFieldDefinition;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ResumeFieldDefinitionMapper extends BaseMapper<ResumeFieldDefinition> {
    
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
}