package club.boyuan.official.domain.resume.service.impl;

import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;
import java.util.Set;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.persistence.mapper.ResumeFieldDefinitionMapper;
import club.boyuan.official.persistence.mapper.ResumeFieldValueMapper;
import club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService;
import lombok.AllArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class ResumeFieldDefinitionServiceImpl implements IResumeFieldDefinitionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumeFieldDefinitionServiceImpl.class);
    
    private final ResumeFieldDefinitionMapper resumeFieldDefinitionMapper;
    private final ResumeFieldValueMapper resumeFieldValueMapper;
    private final SqlSessionFactory sqlSessionFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Override
    public List<ResumeFieldDefinition> getFieldDefinitionsByCycleId(Integer cycleId) {
        logger.debug("查询{}年份的简历字段定义", cycleId);
        try {
            return resumeFieldDefinitionMapper.findByCycleId(cycleId);
        } catch (Exception e) {
            logger.error("查询年份简历字段定义失败，年份: {}", cycleId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_DEFINITION_QUERY_FAILED);
        }
    }
    
    @Override
    public ResumeFieldDefinition getFieldDefinitionById(Integer fieldId) {
        logger.debug("根据ID{}查询简历字段定义", fieldId);
        try {
            // 尝试从Redis缓存中获取数据
            String cacheKey = "field_definition:" + fieldId;
            ResumeFieldDefinition cachedDefinition = null;
            
            try {
                Object cachedObject = redisTemplate.opsForValue().get(cacheKey);
                
                if (cachedObject != null) {
                    // 检查缓存对象类型并进行适当转换
                    if (cachedObject instanceof ResumeFieldDefinition) {
                        cachedDefinition = (ResumeFieldDefinition) cachedObject;
                        logger.debug("从Redis缓存中获取到字段定义，字段ID: {}", fieldId);
                    } else {
                        // 如果类型不匹配，删除损坏的缓存数据
                        logger.warn("缓存中对象类型不匹配，将清除损坏的缓存数据，字段ID: {}", fieldId);
                        redisTemplate.delete(cacheKey);
                    }
                }
            } catch (Exception redisException) {
                // Redis反序列化失败，记录警告并清除损坏的缓存
                logger.warn("从Redis获取数据失败，将清除损坏的缓存数据，字段ID: {}, 错误: {}", 
                          fieldId, redisException.getMessage());
                try {
                    redisTemplate.delete(cacheKey);
                } catch (Exception deleteException) {
                    logger.error("清除损坏缓存失败，字段ID: {}", fieldId, deleteException);
                }
            }
            
            // 如果缓存命中，直接返回
            if (cachedDefinition != null) {
                return cachedDefinition;
            }
            
            // 缓存未命中或类型不匹配，从数据库查询
            ResumeFieldDefinition definition = resumeFieldDefinitionMapper.findById(fieldId);
            
            // 将查询结果存入Redis缓存，设置过期时间为1小时
            if (definition != null) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, definition, 1, TimeUnit.HOURS);
                    logger.debug("将字段定义存入Redis缓存，字段ID: {}，过期时间1小时", fieldId);
                } catch (Exception cacheException) {
                    // 缓存存储失败不影响主流程，只记录警告
                    logger.warn("存储字段定义到Redis缓存失败，字段ID: {}", fieldId, cacheException);
                }
            }
            
            return definition;
        } catch (Exception e) {
            logger.error("根据ID查询简历字段定义失败，字段ID: {}", fieldId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_DEFINITION_QUERY_FAILED);
        }
    }
    
    @Override
    public ResumeFieldDefinition createFieldDefinition(ResumeFieldDefinition fieldDefinition) {
        logger.info("创建简历字段定义，年份: {}，字段键名: {}", 
                fieldDefinition.getCycleId(), fieldDefinition.getFieldKey());
        try {
            applyFieldDefinitionDefaults(fieldDefinition);
            resumeFieldDefinitionMapper.insert(fieldDefinition);
            return fieldDefinition;
        } catch (Exception e) {
            logger.error("创建简历字段定义失败，年份: {}，字段键名: {}", 
                    fieldDefinition.getCycleId(), fieldDefinition.getFieldKey(), e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_DEFINITION_CREATE_FAILED);
        }
    }
    
    @Override
    public ResumeFieldDefinition updateFieldDefinition(ResumeFieldDefinition fieldDefinition) {
        logger.info("更新简历字段定义，字段ID: {}，字段键名: {}", 
                fieldDefinition.getFieldId(), fieldDefinition.getFieldKey());
        try {
            fieldDefinition.setUpdatedAt(LocalDateTime.now());
            // 使用MyBatis-Plus的updateById方法根据ID更新实体
            resumeFieldDefinitionMapper.updateById(fieldDefinition);
            
            // 清除相关缓存
            clearCacheByFieldId(fieldDefinition.getFieldId());
            
            return fieldDefinition;
        } catch (Exception e) {
            logger.error("更新简历字段定义失败，字段ID: {}，字段键名: {}", 
                    fieldDefinition.getFieldId(), fieldDefinition.getFieldKey(), e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_DEFINITION_UPDATE_FAILED);
        }
    }
    
    @Override
    @Transactional
    public List<ResumeFieldDefinition> initFieldDefinitions(Integer cycleId, List<ResumeFieldDefinition> templates) {
        if (cycleId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "cycleId 不能为空");
        }
        if (templates == null || templates.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "字段模板不能为空");
        }

        Map<String, ResumeFieldDefinition> existing = resumeFieldDefinitionMapper
                .selectList(new LambdaQueryWrapper<ResumeFieldDefinition>()
                        .eq(ResumeFieldDefinition::getCycleId, cycleId))
                .stream()
                .collect(Collectors.toMap(ResumeFieldDefinition::getFieldKey,
                        d -> d, (a, b) -> a));

        int created = 0;
        int backfilled = 0;
        for (ResumeFieldDefinition t : templates) {
            if (t.getFieldKey() == null || t.getFieldKey().isBlank()) {
                continue;
            }
            ResumeFieldDefinition found = existing.get(t.getFieldKey());
            if (found != null) {
                // 字段已存在:不动 key/label/type(那是"只补不存在"的承诺),
                // 但把空着的 options 回填上。场景:options 列(V25)上线之前就点过
                // 「加载默认配置」的周期,行都在、选项全是 NULL —— 再点一次只会
                // 全部跳过,选项永远补不上,管理员只能逐个字段手敲。
                // 只在现值为 NULL 时回填,绝不覆盖管理员配置过的选项。
                if (found.getOptions() == null
                        && t.getOptions() != null && !t.getOptions().isEmpty()) {
                    ResumeFieldDefinition patch = new ResumeFieldDefinition();
                    patch.setFieldId(found.getFieldId());
                    patch.setOptions(t.getOptions());
                    resumeFieldDefinitionMapper.updateById(patch);
                    clearCacheByFieldId(found.getFieldId());
                    backfilled++;
                }
                continue;
            }
            ResumeFieldDefinition row = new ResumeFieldDefinition();
            // 刻意不沿用模板的 fieldId:前端本地模板的 ID 是顺序编号,与真实行无关,
            // 照抄会重演「按假 ID 覆盖错误行」的事故。主键一律交给自增。
            row.setCycleId(cycleId);
            row.setFieldKey(t.getFieldKey());
            row.setFieldLabel(t.getFieldLabel());
            row.setFieldType(t.getFieldType());
            row.setPlaceholder(t.getPlaceholder());
            row.setIsRequired(t.getIsRequired());
            row.setSortOrder(t.getSortOrder());
            row.setIsActive(t.getIsActive() == null ? Boolean.TRUE : t.getIsActive());
            row.setOptions(t.getOptions());   // V25 起该列存在，选项随初始化一起落库
            resumeFieldDefinitionMapper.insert(row);
            existing.put(t.getFieldKey(), row);
            created++;
        }

        logger.info("周期 {} 初始化字段定义完成：新建 {} 个，为已存在字段回填选项 {} 个",
                cycleId, created, backfilled);
        return getFieldDefinitionsByCycleId(cycleId);
    }

    @Override
    public List<ResumeFieldDefinition> batchUpdateFieldDefinitions(List<ResumeFieldDefinition> fieldDefinitions) {
        logger.info("批量更新简历字段定义，字段数量: {}", fieldDefinitions.size());
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            ResumeFieldDefinitionMapper batchMapper = sqlSession.getMapper(ResumeFieldDefinitionMapper.class);
            
            // 逐项校验后再更新。此前是无条件 updateById —— fieldId 不存在时 MyBatis-Plus
            // 影响 0 行却不报错，调用方看到「保存成功」，字段实际凭空消失；
            // 而命中别的行时会把内容覆盖上去。前端本地默认模板的 fieldId 是 1..20 的
            // 顺序编号，正是靠这个静默行为把线上字段定义整体错位覆盖的。
            for (ResumeFieldDefinition fieldDefinition : fieldDefinitions) {
                Integer fieldId = fieldDefinition.getFieldId();
                if (fieldId == null || fieldId <= 0) {
                    throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                            "批量更新要求每项都带真实的 fieldId；新增字段请走创建接口。收到: " + fieldId);
                }

                ResumeFieldDefinition existing = resumeFieldDefinitionMapper.selectById(fieldId);
                if (existing == null) {
                    throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                            "字段定义 " + fieldId + " 不存在，拒绝批量更新（此前会静默跳过，导致字段丢失）");
                }
                if (fieldDefinition.getCycleId() != null
                        && !fieldDefinition.getCycleId().equals(existing.getCycleId())) {
                    throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                            "字段定义 " + fieldId + " 属于周期 " + existing.getCycleId()
                                    + "，不能改判到周期 " + fieldDefinition.getCycleId());
                }

                // field_key 是数据契约:resume_field_value 只存 field_id，
                // 一旦某个定义已被简历引用，改它的 key 等于把历史数据的语义偷换掉
                // （线上就出现过「年级」那一格里存着姓名）。label/placeholder 可以随便改。
                boolean keyChanged = fieldDefinition.getFieldKey() != null
                        && !fieldDefinition.getFieldKey().equals(existing.getFieldKey());
                if (keyChanged) {
                    Long referenced = resumeFieldValueMapper.selectCount(
                            new LambdaQueryWrapper<ResumeFieldValue>()
                                    .eq(ResumeFieldValue::getFieldId, fieldId));
                    if (referenced != null && referenced > 0) {
                        throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                                "字段定义 " + fieldId + "（" + existing.getFieldKey() + "）已被 "
                                        + referenced + " 条简历数据引用，不允许修改 field_key。"
                                        + "要更换字段请新建定义并把旧定义停用。");
                    }
                }

                fieldDefinition.setUpdatedAt(LocalDateTime.now());
                batchMapper.updateById(fieldDefinition);
                clearCacheByFieldId(fieldId);
            }
            
            // 提交批处理
            sqlSession.commit();
            
            return fieldDefinitions;
        } catch (Exception e) {
            logger.error("批量更新简历字段定义失败，字段数量: {}", fieldDefinitions.size(), e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_DEFINITION_UPDATE_FAILED);
        }
    }
    
    @Override
    public void deleteFieldDefinition(Integer fieldId) {
        logger.info("删除简历字段定义，字段ID: {}", fieldId);
        try {
            // 先删除与该字段定义关联的所有字段值
            resumeFieldValueMapper.deleteByFieldId(fieldId);
            // 再删除字段定义本身
            resumeFieldDefinitionMapper.deleteById(fieldId);
            
            // 清除相关缓存
            clearCacheByFieldId(fieldId);
        } catch (Exception e) {
            logger.error("删除简历字段定义失败，字段ID: {}", fieldId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_DEFINITION_DELETE_FAILED);
        }
    }
    
    /**
     * 根据字段ID清除相关缓存
     *
     */
    private void applyFieldDefinitionDefaults(ResumeFieldDefinition fieldDefinition) {
        if (fieldDefinition.getFieldType() == null || fieldDefinition.getFieldType().isBlank()) {
            fieldDefinition.setFieldType("text");
        }
        if (fieldDefinition.getIsRequired() == null) {
            fieldDefinition.setIsRequired(false);
        }
        if (fieldDefinition.getSortOrder() == null) {
            fieldDefinition.setSortOrder(0);
        }
        if (fieldDefinition.getIsActive() == null) {
            fieldDefinition.setIsActive(true);
        }
    }

    private void clearCacheByFieldId(Integer fieldId) {
        try {
            String cacheKey = "field_definition:" + fieldId;
            redisTemplate.delete(cacheKey);
            logger.debug("清除字段定义缓存: {}", cacheKey);
        } catch (Exception e) {
            logger.warn("清除字段定义缓存失败，字段ID: {}", fieldId, e);
        }
    }
}