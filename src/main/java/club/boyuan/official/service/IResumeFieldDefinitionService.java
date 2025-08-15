package club.boyuan.official.service;

import club.boyuan.official.entity.ResumeFieldDefinition;

import java.util.List;

public interface IResumeFieldDefinitionService {
    
    /**
     * 根据招聘年份ID获取字段定义列表
     * @param cycleId 招聘年份ID
     * @return 字段定义列表
     */
    List<ResumeFieldDefinition> getFieldDefinitionsByCycleId(Integer cycleId);
    
    /**
     * 根据字段ID获取字段定义
     * @param fieldId 字段ID
     * @return 字段定义
     */
    ResumeFieldDefinition getFieldDefinitionById(Integer fieldId);
    
    /**
     * 创建字段定义
     * @param fieldDefinition 字段定义实体
     * @return 创建后的字段定义
     */
    ResumeFieldDefinition createFieldDefinition(ResumeFieldDefinition fieldDefinition);
    
    /**
     * 更新字段定义
     * @param fieldDefinition 字段定义实体
     * @return 更新后的字段定义
     */
    ResumeFieldDefinition updateFieldDefinition(ResumeFieldDefinition fieldDefinition);
    
    /**
     * 删除字段定义
     * @param fieldId 字段ID
     */
    void deleteFieldDefinition(Integer fieldId);
}