package club.boyuan.official.service.impl;

import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.ResumeFieldDefinitionMapper;
import club.boyuan.official.mapper.ResumeFieldValueMapper;
import club.boyuan.official.service.IResumeFieldDefinitionService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ResumeFieldDefinitionServiceImpl implements IResumeFieldDefinitionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumeFieldDefinitionServiceImpl.class);
    
    private final ResumeFieldDefinitionMapper resumeFieldDefinitionMapper;
    private final ResumeFieldValueMapper resumeFieldValueMapper;
    
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
            return resumeFieldDefinitionMapper.findById(fieldId);
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
            return fieldDefinition;
        } catch (Exception e) {
            logger.error("更新简历字段定义失败，字段ID: {}，字段键名: {}", 
                    fieldDefinition.getFieldId(), fieldDefinition.getFieldKey(), e);
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
        } catch (Exception e) {
            logger.error("删除简历字段定义失败，字段ID: {}", fieldId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_DEFINITION_DELETE_FAILED);
        }
    }
}