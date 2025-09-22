package club.boyuan.official.service.impl;

import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.ResumeFieldDefinitionMapper;
import club.boyuan.official.mapper.ResumeFieldValueMapper;
import club.boyuan.official.service.IResumeFieldDefinitionService;
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
            resumeFieldDefinitionMapper.update(fieldDefinition);
            
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
    public List<ResumeFieldDefinition> batchUpdateFieldDefinitions(List<ResumeFieldDefinition> fieldDefinitions) {
        logger.info("批量更新简历字段定义，字段数量: {}", fieldDefinitions.size());
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            ResumeFieldDefinitionMapper batchMapper = sqlSession.getMapper(ResumeFieldDefinitionMapper.class);
            
            // 批量更新字段定义
            for (ResumeFieldDefinition fieldDefinition : fieldDefinitions) {
                fieldDefinition.setUpdatedAt(LocalDateTime.now());
                batchMapper.update(fieldDefinition);
                
                // 清除相关缓存
                clearCacheByFieldId(fieldDefinition.getFieldId());
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
     * @param fieldId 字段ID
     */
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