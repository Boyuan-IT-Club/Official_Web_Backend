package club.boyuan.official.domain.resume.service;

import club.boyuan.official.persistence.entity.ResumeFieldDefinition;

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
     * 批量更新字段定义
     * @param fieldDefinitions 字段定义实体列表
     * @return 更新后的字段定义列表
     */
    List<ResumeFieldDefinition> batchUpdateFieldDefinitions(List<ResumeFieldDefinition> fieldDefinitions);
    
    /**
     * 删除字段定义
     * @param fieldId 字段ID
     */
    void deleteFieldDefinition(Integer fieldId);

    /**
     * 按模板为某周期初始化字段定义:只补该周期尚不存在的 field_key,已存在的一律不动。
     *
     * 刻意忽略模板里带的 fieldId —— 前端本地默认模板的 fieldId 是 1..20 的顺序编号,
     * 与真实行毫无关系。之前缺了这个接口,前端「加载默认配置」拿到 404 后回退到
     * 本地模板再走批量更新,导致 updateById 按那些假 ID 覆盖了错误的行(见 ADR/事故记录)。
     *
     * @return 初始化后该周期的全部字段定义
     */
    List<ResumeFieldDefinition> initFieldDefinitions(Integer cycleId, List<ResumeFieldDefinition> templates);

    /**
     * 批量按 ID 取字段定义,返回 fieldId -> 定义。
     *
     * 组装一份简历要用到约 20 个字段定义,逐个调 getFieldDefinitionById 就是
     * 20 次 Redis 往返(N+1);面试时一位位点开候选人,这个代价每次都付一遍。
     * 本方法一次 IN 查询取回,自身不再走单点缓存。
     */
    java.util.Map<Integer, ResumeFieldDefinition> getFieldDefinitionsByIds(java.util.Collection<Integer> fieldIds);
}