package club.boyuan.official.service.impl;

import club.boyuan.official.dto.PageResultDTO;
import club.boyuan.official.dto.ResumeDTO;
import club.boyuan.official.dto.ResumeFieldValueDTO;
import club.boyuan.official.dto.SimpleResumeFieldDTO;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldValue;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.ResumeFieldValueMapper;
import club.boyuan.official.mapper.ResumeMapper;
import club.boyuan.official.service.IResumeFieldDefinitionService;
import club.boyuan.official.service.IResumeService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ResumeServiceImpl implements IResumeService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumeServiceImpl.class);
    
    private final ResumeMapper resumeMapper;
    private final ResumeFieldValueMapper resumeFieldValueMapper;
    private final IResumeFieldDefinitionService fieldDefinitionService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis缓存键前缀
    private static final String RESUME_CACHE_PREFIX = "resumes:cycle:";
    private static final String QUERY_RESUME_CACHE_PREFIX = "resumes:query:";
    private static final long CACHE_EXPIRE_TIME = 300; // 缓存过期时间(秒)

    @Override
    public Resume getResumeByUserIdAndCycleId(Integer userId, Integer cycleId) {
        logger.debug("查询用户{}在{}年的简历", userId, cycleId);
        try {
            return resumeMapper.findByUserIdAndCycleId(userId, cycleId);
        } catch (Exception e) {
            logger.error("查询用户简历失败，用户ID: {}，年份: {}", userId, cycleId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    @Override
    public Resume getResumeById(Integer resumeId) {
        logger.debug("根据ID{}查询简历", resumeId);
        try {
            return resumeMapper.findById(resumeId);
        } catch (Exception e) {
            logger.error("根据ID查询简历失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    @Override
    public List<Resume> getResumesByUserId(Integer userId) {
        logger.debug("查询用户{}的所有简历", userId);
        try {
            return resumeMapper.findByUserId(userId);
        } catch (Exception e) {
            logger.error("查询用户所有简历失败，用户ID: {}", userId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    @Override
    @Transactional
    public Resume createResume(Resume resume) {
        logger.info("创建简历，用户ID: {}，年份: {}", resume.getUserId(), resume.getCycleId());
        try {
            resumeMapper.insert(resume);
            // 清除相关缓存
            clearCacheByCycleId(resume.getCycleId());
            return resume;
        } catch (Exception e) {
            logger.error("创建简历失败，用户ID: {}，年份: {}", resume.getUserId(), resume.getCycleId(), e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_CREATE_FAILED);
        }
    }
    
    @Override
    @Transactional
    public Resume updateResume(Resume resume) {
        logger.info("更新简历，简历ID: {}", resume.getResumeId());
        try {
            Resume oldResume = resumeMapper.findById(resume.getResumeId());
            resumeMapper.update(resume);
            // 清除相关缓存
            if (oldResume != null) {
                clearCacheByCycleId(oldResume.getCycleId());
            }
            return resume;
        } catch (Exception e) {
            logger.error("更新简历失败，简历ID: {}", resume.getResumeId(), e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_UPDATE_FAILED);
        }
    }
    
    @Override
    @Transactional
    public void deleteResume(Integer resumeId) {
        logger.info("删除简历，简历ID: {}", resumeId);
        try {
            // 获取简历信息用于清除缓存
            Resume resume = resumeMapper.findById(resumeId);
            // 先删除字段值
            resumeFieldValueMapper.deleteByResumeId(resumeId);
            // 再删除简历
            resumeMapper.deleteById(resumeId);
            // 清除相关缓存
            if (resume != null) {
                clearCacheByCycleId(resume.getCycleId());
            }
        } catch (Exception e) {
            logger.error("删除简历失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_DELETE_FAILED);
        }
    }
    
    @Override
    @Transactional
    public Resume submitResume(Integer resumeId) {
        logger.info("提交简历，简历ID: {}", resumeId);
        try {
            Resume resume = resumeMapper.findById(resumeId);
            if (resume != null) {
                resume.setStatus(2); // 设置为已提交状态
                resume.setSubmittedAt(LocalDateTime.now());
                resumeMapper.update(resume);
                // 清除相关缓存
                clearCacheByCycleId(resume.getCycleId());
            }
            return resume;
        } catch (Exception e) {
            logger.error("提交简历失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_SUBMIT_FAILED, e.getMessage() != null ? e.getMessage() : "提交简历失败");
        }
    }
    
    @Override
    @Transactional
    public void saveFieldValues(List<ResumeFieldValue> fieldValues) {
        logger.info("保存简历字段值，数量: {}", fieldValues.size());
        try {
            List<ResumeFieldValue> toInsert = new ArrayList<>();
            List<ResumeFieldValue> toUpdate = new ArrayList<>();
            
            Integer cycleId = null;
            
            for (ResumeFieldValue fieldValue : fieldValues) {
                ResumeFieldValue existingValue = resumeFieldValueMapper.findByResumeIdAndFieldId(
                        fieldValue.getResumeId(), fieldValue.getFieldId());
                if (existingValue != null) {
                    // 更新已存在的字段值
                    existingValue.setFieldValue(fieldValue.getFieldValue());
                    toUpdate.add(existingValue);
                } else {
                    // 插入新的字段值
                    toInsert.add(fieldValue);
                }
                
                // 获取cycleId用于清除缓存
                if (cycleId == null) {
                    Resume resume = resumeMapper.findById(fieldValue.getResumeId());
                    if (resume != null) {
                        cycleId = resume.getCycleId();
                    }
                }
            }
            
            // 批量插入新字段值
            if (!toInsert.isEmpty()) {
                logger.debug("批量插入{}个新字段值", toInsert.size());
                resumeFieldValueMapper.batchInsert(toInsert);
            }
            
            // 批量更新已存在的字段值
            if (!toUpdate.isEmpty()) {
                logger.debug("批量更新{}个字段值", toUpdate.size());
                resumeFieldValueMapper.batchUpdate(toUpdate);
            }
            
            // 清除相关缓存
            if (cycleId != null) {
                clearCacheByCycleId(cycleId);
            }
        } catch (Exception e) {
            logger.error("保存简历字段值失败，字段值数量: {}", fieldValues.size(), e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_FIELD_VALUE_SAVE_FAILED, e.getMessage() != null ? e.getMessage() : "保存简历字段值失败");
        }
    }
    
    @Override
    public List<ResumeFieldValue> getFieldValuesByResumeId(Integer resumeId) {
        logger.debug("根据简历ID{}获取字段值", resumeId);
        try {
            return resumeFieldValueMapper.findByResumeId(resumeId);
        } catch (Exception e) {
            logger.error("根据简历ID获取字段值失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED);
        }
    }
    
    @Override
    public List<ResumeFieldValueDTO> getFieldValuesWithDefinitionsByResumeId(Integer resumeId) {
        logger.debug("根据简历ID{}获取字段值及定义信息", resumeId);
        try {
            List<ResumeFieldValue> fieldValues = resumeFieldValueMapper.findByResumeId(resumeId);
            
            return fieldValues.stream().map(fieldValue -> {
                ResumeFieldValueDTO dto = new ResumeFieldValueDTO();
                dto.setValueId(fieldValue.getValueId());
                dto.setResumeId(fieldValue.getResumeId());
                dto.setFieldId(fieldValue.getFieldId());
                dto.setFieldValue(fieldValue.getFieldValue());
                dto.setCreatedAt(fieldValue.getCreatedAt());
                dto.setUpdatedAt(fieldValue.getUpdatedAt());
                
                // 获取并设置字段标签
                if (fieldValue.getFieldId() != null) {
                    var fieldDefinition = fieldDefinitionService.getFieldDefinitionById(fieldValue.getFieldId());
                    if (fieldDefinition != null) {
                        dto.setFieldLabel(fieldDefinition.getFieldLabel());
                    }
                }
                
                return dto;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("根据简历ID获取字段值及定义信息失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED);
        }
    }
    
    @Override
    public List<ResumeDTO> queryResumes(String name, String major, Integer cycleId, String status) {
        logger.info("条件查询简历：name={}, major={}, cycleId={}, status={}", name, major, cycleId, status);
        // 构建缓存键
        String cacheKey = QUERY_RESUME_CACHE_PREFIX + "name:" + (name != null ? name : "") 
                + ":major:" + (major != null ? major : "") 
                + ":cycleId:" + (cycleId != null ? cycleId : "") 
                + ":status:" + (status != null ? status : "");
        
        try {
            // 尝试从缓存中获取
            List<ResumeDTO> cachedResult = (List<ResumeDTO>) redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) {
                logger.debug("从缓存中获取条件查询简历结果，缓存键: {}", cacheKey);
                return cachedResult;
            }
            
            // 缓存未命中，从数据库查询
            List<Resume> resumes = resumeMapper.queryResumes(name, major, cycleId, status);
            List<ResumeDTO> result = new ArrayList<>();
            for (Resume resume : resumes) {
                ResumeDTO dto = new ResumeDTO();
                dto.setResumeId(resume.getResumeId());
                dto.setUserId(resume.getUserId());
                dto.setCycleId(resume.getCycleId());
                dto.setStatus(resume.getStatus());
                dto.setSubmittedAt(resume.getSubmittedAt());
                dto.setCreatedAt(resume.getCreatedAt());
                dto.setUpdatedAt(resume.getUpdatedAt());
                // 可选：添加简化字段信息
                List<SimpleResumeFieldDTO> simpleFields = getSimpleFieldValuesByResumeId(resume.getResumeId());
                dto.setSimpleFields(simpleFields);
                result.add(dto);
            }
            
            // 将结果存入缓存
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
            logger.debug("将条件查询简历结果存入缓存，缓存键: {}", cacheKey);
            
            return result;
        } catch (Exception e) {
            logger.error("条件查询简历失败", e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    @Override
    public PageResultDTO<ResumeDTO> queryResumesWithPagination(String name, String major, Integer cycleId, String status, int page, int size) {
        logger.info("分页条件查询简历：name={}, major={}, cycleId={}, status={}, page={}, size={}", name, major, cycleId, status, page, size);
        
        try {
            // 参数校验
            if (page < 0) page = 0;
            if (size <= 0) size = 10;
            if (size > 100) size = 100; // 限制最大分页大小
            
            // 计算偏移量
            int offset = page * size;
            
            // 查询总数
            int totalElements = resumeMapper.countResumes(name, major, cycleId, status);
            
            // 查询数据
            List<Resume> resumes = resumeMapper.queryResumesWithPagination(name, major, cycleId, status, offset, size);
            
            // 转换为DTO
            List<ResumeDTO> result = new ArrayList<>();
            for (Resume resume : resumes) {
                ResumeDTO dto = new ResumeDTO();
                dto.setResumeId(resume.getResumeId());
                dto.setUserId(resume.getUserId());
                dto.setCycleId(resume.getCycleId());
                dto.setStatus(resume.getStatus());
                dto.setSubmittedAt(resume.getSubmittedAt());
                dto.setCreatedAt(resume.getCreatedAt());
                dto.setUpdatedAt(resume.getUpdatedAt());
                // 可选：添加简化字段信息
                List<SimpleResumeFieldDTO> simpleFields = getSimpleFieldValuesByResumeId(resume.getResumeId());
                dto.setSimpleFields(simpleFields);
                result.add(dto);
            }
            
            // 计算分页信息
            int totalPages = (int) Math.ceil((double) totalElements / size);
            boolean first = page == 0;
            boolean last = page >= totalPages - 1;
            
            PageResultDTO<ResumeDTO> pageResult = new PageResultDTO<>(result, totalElements, totalPages, page, size, first, last);
            
            logger.info("分页条件查询简历完成：总记录数={}, 总页数={}, 当前页={}, 当前记录数={}", totalElements, totalPages, page, result.size());
            
            return pageResult;
        } catch (Exception e) {
            logger.error("分页条件查询简历失败", e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    @Override
    public ResumeDTO getResumeWithFieldValues(Integer userId, Integer cycleId) {
        logger.debug("获取用户{}在{}年的简历及字段值", userId, cycleId);
        try {
            // 获取简历基本信息
            Resume resume = resumeMapper.findByUserIdAndCycleId(userId, cycleId);
            if (resume == null) {
                return null;
            }
            
            // 构造ResumeDTO
            ResumeDTO resumeDTO = new ResumeDTO();
            resumeDTO.setResumeId(resume.getResumeId());
            resumeDTO.setUserId(resume.getUserId());
            resumeDTO.setCycleId(resume.getCycleId());
            resumeDTO.setStatus(resume.getStatus());
            resumeDTO.setSubmittedAt(resume.getSubmittedAt());
            resumeDTO.setCreatedAt(resume.getCreatedAt());
            resumeDTO.setUpdatedAt(resume.getUpdatedAt());
            
            // 获取简化版字段信息（仅包含字段标签和字段值）
            List<SimpleResumeFieldDTO> simpleFields = getSimpleFieldValuesByResumeId(resume.getResumeId());
            resumeDTO.setSimpleFields(simpleFields);
            
            return resumeDTO;
        } catch (Exception e) {
            logger.error("获取用户简历及字段值失败，用户ID: {}，年份: {}", userId, cycleId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    @Override
    public ResumeDTO getResumeWithFieldValuesById(Integer resumeId) {
        logger.debug("根据简历ID{}获取简历及字段值", resumeId);
        try {
            // 获取简历基本信息
            Resume resume = resumeMapper.findById(resumeId);
            if (resume == null) {
                return null;
            }
            
            // 构造ResumeDTO
            ResumeDTO resumeDTO = new ResumeDTO();
            resumeDTO.setResumeId(resume.getResumeId());
            resumeDTO.setUserId(resume.getUserId());
            resumeDTO.setCycleId(resume.getCycleId());
            resumeDTO.setStatus(resume.getStatus());
            resumeDTO.setSubmittedAt(resume.getSubmittedAt());
            resumeDTO.setCreatedAt(resume.getCreatedAt());
            resumeDTO.setUpdatedAt(resume.getUpdatedAt());
            
            // 获取简化版字段信息（仅包含字段标签和字段值）
            List<SimpleResumeFieldDTO> simpleFields = getSimpleFieldValuesByResumeId(resume.getResumeId());
            resumeDTO.setSimpleFields(simpleFields);
            
            return resumeDTO;
        } catch (Exception e) {
            logger.error("根据简历ID获取简历及字段值失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    /**
     * 根据简历ID获取简化版字段信息（仅包含字段标签和字段值）
     * @param resumeId 简历ID
     * @return 简化版字段信息列表
     */
    private List<SimpleResumeFieldDTO> getSimpleFieldValuesByResumeId(Integer resumeId) {
        try {
            List<ResumeFieldValue> fieldValues = resumeFieldValueMapper.findByResumeId(resumeId);
            
            return fieldValues.stream().map(fieldValue -> {
                String fieldLabel = "";
                if (fieldValue.getFieldId() != null) {
                    var fieldDefinition = fieldDefinitionService.getFieldDefinitionById(fieldValue.getFieldId());
                    if (fieldDefinition != null) {
                        fieldLabel = fieldDefinition.getFieldLabel();
                    }
                }
                return new SimpleResumeFieldDTO(fieldValue.getFieldId(), fieldLabel, fieldValue.getFieldValue());
            }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取简化版字段信息失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED);
        }
    }
    
    @Override
    public List<Resume> getAllResumesByCycleId(Integer cycleId) {
        logger.debug("获取招募周期 {} 下的所有简历", cycleId);
        String cacheKey = RESUME_CACHE_PREFIX + cycleId;
        
        try {
            // 尝试从缓存中获取
            List<Resume> cachedResumes = (List<Resume>) redisTemplate.opsForValue().get(cacheKey);
            if (cachedResumes != null) {
                logger.debug("从缓存中获取招募周期 {} 下的所有简历", cycleId);
                return cachedResumes;
            }
            
            // 缓存未命中，从数据库查询
            List<Resume> resumes = resumeMapper.findByCycleId(cycleId);
            
            // 将结果存入缓存
            redisTemplate.opsForValue().set(cacheKey, resumes, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
            logger.debug("将招募周期 {} 下的所有简历存入缓存", cycleId);
            
            return resumes;
        } catch (Exception e) {
            logger.error("获取招募周期下的所有简历失败，招募周期ID: {}", cycleId, e);
            throw new BusinessException(BusinessExceptionEnum.RESUME_QUERY_FAILED);
        }
    }
    
    /**
     * 清除指定招募周期的简历缓存
     * @param cycleId 招募周期ID
     */
    private void clearCacheByCycleId(Integer cycleId) {
        if (cycleId != null) {
            String cacheKey = RESUME_CACHE_PREFIX + cycleId;
            redisTemplate.delete(cacheKey);
            logger.debug("清除招募周期 {} 的简历缓存", cycleId);
        }
    }
}