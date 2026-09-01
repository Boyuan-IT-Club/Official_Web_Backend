package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.ResumeFieldValueDTO;
import club.boyuan.official.domain.resume.dto.SimpleResumeFieldDTO;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.persistence.mapper.ResumeFieldValueMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService;
import club.boyuan.official.domain.resume.service.IResumeService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ResumeServiceImpl implements IResumeService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResumeServiceImpl.class);
    
    private final ResumeMapper resumeMapper;
    private final RecruitmentCycleMapper recruitmentCycleMapper;
    private final club.boyuan.official.persistence.mapper.UserMapper userMapper;
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
        requireCycleOpen(resume.getCycleId());
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
            resumeMapper.updateById(resume);
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
        // 周期关闭后不再接收提交。放在这里而不是只藏前端入口：
        // 前端把开放周期从切换器里拿掉只是看不见，直接调接口照样能投。
        // 必须在 try 之外 —— 下面的 catch(Exception) 会把一切重包装成
        // RESUME_SUBMIT_FAILED(3008)，把守卫的专属错误码 3010 吞掉（CI 实测抓到）。
        Resume guarded = resumeMapper.findById(resumeId);
        if (guarded != null) {
            requireCycleOpen(guarded.getCycleId());
        }
        try {
            Resume resume = resumeMapper.findById(resumeId);
            if (resume != null) {
                resume.setStatus(2); // 设置为已提交状态
                resume.setSubmittedAt(LocalDateTime.now());
                resumeMapper.updateById(resume);
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
                
                if (fieldValue.getFieldId() != null) {
                    ResumeFieldDefinition fieldDefinition =
                            fieldDefinitionService.getFieldDefinitionById(fieldValue.getFieldId());
                    applyFieldDefinitionToValueDto(dto, fieldDefinition);
                }

                return dto;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("根据简历ID获取字段值及定义信息失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED);
        }
    }

    @Override
    public List<ResumeDTO> queryResumes(String name, String major, String expectedDepartment, Integer cycleId, String status) {
        logger.info("条件查询简历：name={}, major={}, expectedDepartment={}, cycleId={}, status={}", name, major, expectedDepartment, cycleId, status);
        // 构建缓存键
        String cacheKey = QUERY_RESUME_CACHE_PREFIX + "name:" + (name != null ? name : "") 
                + ":major:" + (major != null ? major : "")
                + ":expectedDepartment:" + (expectedDepartment != null ? expectedDepartment : "")
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
            List<Resume> resumes = resumeMapper.queryResumes(name, major, expectedDepartment, cycleId, status);
            List<ResumeDTO> result = new ArrayList<>();
            // 一次批量取回本页所有简历的字段值，替掉循环里的逐份查询
            java.util.Map<Integer, List<SimpleResumeFieldDTO>> fieldsByResume =
                    getSimpleFieldValuesByResumeIds(
                            resumes.stream().map(Resume::getResumeId).collect(Collectors.toList()));
            java.util.Map<Integer, String> scorerNames = resolveScorerNames(resumes);
            java.util.Map<Integer, club.boyuan.official.persistence.entity.User> candidates = resolveCandidateUsers(resumes);
            for (Resume resume : resumes) {
                ResumeDTO dto = new ResumeDTO();
                dto.setResumeId(resume.getResumeId());
                dto.setUserId(resume.getUserId());
                dto.setCycleId(resume.getCycleId());
                dto.setStatus(resume.getStatus());
                dto.setResumeScore(resume.getResumeScore());
                fillScorer(dto, resume, scorerNames);
                fillCandidateUser(dto, resume, candidates);
                dto.setSubmittedAt(resume.getSubmittedAt());
                dto.setCreatedAt(resume.getCreatedAt());
                dto.setUpdatedAt(resume.getUpdatedAt());
                // 可选：添加简化字段信息
                dto.setSimpleFields(fieldsByResume.getOrDefault(
                        resume.getResumeId(), java.util.Collections.emptyList()));
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
    public PageResultDTO<ResumeDTO> queryResumesWithPagination(String name, String major, String expectedDepartment, Integer cycleId, String status, int page, int size, String sortBy, String sortOrder) {
        logger.info("分页条件查询简历：name={}, major={}, expectedDepartment={}, cycleId={}, status={}, page={}, size={}, sortBy={}, sortOrder={}", name, major, expectedDepartment, cycleId, status, page, size, sortBy, sortOrder);
        
        try {
            // 参数校验
            if (page < 0) page = 0;
            if (size <= 0) size = 10;
            if (size > 100) size = 100; // 限制最大分页大小
            
            // 计算偏移量
            int offset = page * size;
            
            // 查询总数
            int totalElements = resumeMapper.countResumes(name, major, expectedDepartment, cycleId, status);
            
            // 查询数据
            List<Resume> resumes = resumeMapper.queryResumesWithPagination(name, major, expectedDepartment, cycleId, status, offset, size, sortBy, sortOrder);
            
            // 转换为DTO
            List<ResumeDTO> result = new ArrayList<>();
            // 一次批量取回本页所有简历的字段值，替掉循环里的逐份查询
            java.util.Map<Integer, List<SimpleResumeFieldDTO>> fieldsByResume =
                    getSimpleFieldValuesByResumeIds(
                            resumes.stream().map(Resume::getResumeId).collect(Collectors.toList()));
            java.util.Map<Integer, String> scorerNames = resolveScorerNames(resumes);
            java.util.Map<Integer, club.boyuan.official.persistence.entity.User> candidates = resolveCandidateUsers(resumes);
            for (Resume resume : resumes) {
                ResumeDTO dto = new ResumeDTO();
                dto.setResumeId(resume.getResumeId());
                dto.setUserId(resume.getUserId());
                dto.setCycleId(resume.getCycleId());
                dto.setStatus(resume.getStatus());
                dto.setResumeScore(resume.getResumeScore());
                fillScorer(dto, resume, scorerNames);
                fillCandidateUser(dto, resume, candidates);
                dto.setSubmittedAt(resume.getSubmittedAt());
                dto.setCreatedAt(resume.getCreatedAt());
                dto.setUpdatedAt(resume.getUpdatedAt());
                // 可选：添加简化字段信息
                dto.setSimpleFields(fieldsByResume.getOrDefault(
                        resume.getResumeId(), java.util.Collections.emptyList()));
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
            resumeDTO.setResumeScore(resume.getResumeScore());
            fillScorer(resumeDTO, resume, resolveScorerNames(List.of(resume)));
            fillCandidateUser(resumeDTO, resume, resolveCandidateUsers(List.of(resume)));
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
            resumeDTO.setResumeScore(resume.getResumeScore());
            fillScorer(resumeDTO, resume, resolveScorerNames(List.of(resume)));
            fillCandidateUser(resumeDTO, resume, resolveCandidateUsers(List.of(resume)));
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
    
        @Override
    public Resume updateResumeScore(Integer resumeId, Integer score, Integer scorerUserId) {
        if (resumeId == null || score == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        if (score < 0 || score > 100) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "简历评分需在 0~100 之间，收到: " + score);
        }
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        // 显式 UpdateWrapper 只动打分三列 —— updateById 走整个实体，
        // 会把并发窗口里其它字段的旧值一起写回去
        LocalDateTime scoredAt = LocalDateTime.now();
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getResumeId, resumeId)
                .set(Resume::getResumeScore, score)
                .set(Resume::getScoredBy, scorerUserId)
                .set(Resume::getScoredAt, scoredAt));
        resume.setResumeScore(score);
        resume.setScoredBy(scorerUserId);
        resume.setScoredAt(scoredAt);
        logger.info("简历评分已更新，简历ID: {}，分数: {}，打分人: {}", resumeId, score, scorerUserId);
        return resume;
    }

    /**
     * 周期必须处于开放投递状态：未删除、启用中、今天在起止日期内。
     *
     * 管理员想停止收简历时，改结束日期到昨天或停用周期即可 —— 这里保证
     * 后端真的会拒收，而不是只靠前端把入口藏起来。
     * 拦「新建」「编辑」「提交」三个学生侧动作（编辑原先不拦，导致周期结束后
     * 学生仍能改草稿、造成"填完了却投不进去"的困惑）；已提交简历的展示/审核不受影响。
     */
    private void requireCycleOpen(Integer cycleId) {
        if (cycleId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        club.boyuan.official.persistence.entity.RecruitmentCycle cycle = recruitmentCycleMapper.selectById(cycleId);
        boolean open = cycle != null
                && !Integer.valueOf(1).equals(cycle.getIsDeleted())
                && Integer.valueOf(1).equals(cycle.getIsActive())
                && cycle.getStartDate() != null && cycle.getEndDate() != null
                && !java.time.LocalDate.now().isBefore(cycle.getStartDate())
                && !java.time.LocalDate.now().isAfter(cycle.getEndDate());
        if (!open) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_CYCLE_CLOSED);
        }
    }

    @Override
    public void assertCycleOpen(Integer cycleId) {
        requireCycleOpen(cycleId);
    }

    /**
     * 批量把打分人 userId 解析成姓名。走 UserMapper 的自定义查询
     * （通用查询在 User 上会撞 role 列缺失，见 EvaluationBoardServiceImpl 的说明）。
     */
    private java.util.Map<Integer, String> resolveScorerNames(List<Resume> resumes) {
        java.util.Set<Integer> scorerIds = resumes.stream()
                .map(Resume::getScoredBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (scorerIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return userMapper.selectUsersByIds(new ArrayList<>(scorerIds)).stream()
                .filter(u -> u.getName() != null)
                .collect(Collectors.toMap(
                        club.boyuan.official.persistence.entity.User::getUserId,
                        club.boyuan.official.persistence.entity.User::getName,
                        (a, b) -> a));
    }

    /** DTO 上补打分署名（分数本身各处已在填） */
    private void fillScorer(ResumeDTO dto, Resume resume, java.util.Map<Integer, String> scorerNames) {
        dto.setScoredBy(resume.getScoredBy());
        dto.setScoredByName(resume.getScoredBy() == null ? null : scorerNames.get(resume.getScoredBy()));
    }

    /**
     * 批量取回候选人账号（姓名/邮箱）。简历字段值可能一片空白（自动建的空草稿），
     * 管理端的身份信息以注册账号为准兜底，不再显示「未提供姓名」。
     */
    private java.util.Map<Integer, club.boyuan.official.persistence.entity.User> resolveCandidateUsers(List<Resume> resumes) {
        java.util.Set<Integer> userIds = resumes.stream()
                .map(Resume::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return userMapper.selectUsersByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(
                        club.boyuan.official.persistence.entity.User::getUserId,
                        java.util.function.Function.identity(),
                        (a, b) -> a));
    }

    /** DTO 上补候选人注册姓名/邮箱 */
    private void fillCandidateUser(ResumeDTO dto, Resume resume,
                                   java.util.Map<Integer, club.boyuan.official.persistence.entity.User> candidates) {
        club.boyuan.official.persistence.entity.User user =
                resume.getUserId() == null ? null : candidates.get(resume.getUserId());
        if (user != null) {
            dto.setUserName(user.getName());
            dto.setUserEmail(user.getEmail());
        }
    }

/**
     * 根据简历ID获取简化版字段信息（仅包含字段标签和字段值）
     * @param resumeId 简历ID
     * @return 简化版字段信息列表
     */

    /**
     * 批量取多份简历的简化字段值，返回 resumeId -> 字段列表。
     *
     * 列表页原来是每份简历一次 findByResumeId、再逐字段查定义 ——
     * 一页 100 份简历就是 100 次值查询 + 上千次定义查找。
     * 这里两次查询搞定（值一次 IN、定义一次 IN）。
     */
    private java.util.Map<Integer, List<SimpleResumeFieldDTO>> getSimpleFieldValuesByResumeIds(
            java.util.Collection<Integer> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Set<Integer> ids = resumeIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<ResumeFieldValue> all = resumeFieldValueMapper.findByResumeIds(ids);
        if (all.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<Integer, ResumeFieldDefinition> defs = fieldDefinitionService.getFieldDefinitionsByIds(
                all.stream().map(ResumeFieldValue::getFieldId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));

        java.util.Map<Integer, List<SimpleResumeFieldDTO>> byResume = new java.util.HashMap<>();
        for (ResumeFieldValue fv : all) {
            if (fv.getResumeId() == null) {
                continue;
            }
            byResume.computeIfAbsent(fv.getResumeId(), k -> new ArrayList<>())
                    .add(toSimpleResumeFieldDTO(fv,
                            fv.getFieldId() == null ? null : defs.get(fv.getFieldId())));
        }
        return byResume;
    }

    private List<SimpleResumeFieldDTO> getSimpleFieldValuesByResumeId(Integer resumeId) {
        try {
            List<ResumeFieldValue> fieldValues = resumeFieldValueMapper.findByResumeId(resumeId);
            if (fieldValues.isEmpty()) {
                return new ArrayList<>();
            }
            // 一次批量取回全部字段定义。原来是每个字段值单独调
            // getFieldDefinitionById —— 一份简历约 20 个字段就是 20 次 Redis
            // 往返，而面试官打分时一位位点开候选人，这代价每次重复付。
            java.util.Set<Integer> fieldIds = fieldValues.stream()
                    .map(ResumeFieldValue::getFieldId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            java.util.Map<Integer, ResumeFieldDefinition> defs =
                    fieldDefinitionService.getFieldDefinitionsByIds(fieldIds);

            return fieldValues.stream()
                    .map(fv -> toSimpleResumeFieldDTO(fv,
                            fv.getFieldId() == null ? null : defs.get(fv.getFieldId())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取简化版字段信息失败，简历ID: {}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED);
        }
    }
    
    private void applyFieldDefinitionToValueDto(ResumeFieldValueDTO dto, ResumeFieldDefinition fieldDefinition) {
        if (fieldDefinition == null) {
            return;
        }
        dto.setFieldKey(fieldDefinition.getFieldKey());
        dto.setFieldLabel(fieldDefinition.getFieldLabel());
        dto.setFieldType(fieldDefinition.getFieldType());
        dto.setPlaceholder(fieldDefinition.getPlaceholder());
    }

    private SimpleResumeFieldDTO toSimpleResumeFieldDTO(ResumeFieldValue fieldValue,
                                                        ResumeFieldDefinition fieldDefinition) {
        String fieldKey = fieldDefinition != null ? fieldDefinition.getFieldKey() : null;
        String fieldLabel = fieldDefinition != null ? fieldDefinition.getFieldLabel() : "";
        String fieldType = fieldDefinition != null ? fieldDefinition.getFieldType() : null;
        String placeholder = fieldDefinition != null ? fieldDefinition.getPlaceholder() : null;
        return new SimpleResumeFieldDTO(
                fieldValue.getFieldId(),
                fieldKey,
                fieldLabel,
                fieldType,
                placeholder,
                fieldValue.getFieldValue()
        );
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
            
            // 清除查询缓存 - 使用模式匹配清除所有相关的查询缓存
            String queryPattern = QUERY_RESUME_CACHE_PREFIX + "*:cycleId:" + cycleId + ":*";
            Set<String> keys = redisTemplate.keys(queryPattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            
            // 清除不包含cycleId的通用查询缓存（可能也会受到影响）
            String generalPattern = QUERY_RESUME_CACHE_PREFIX + "*";
            Set<String> generalKeys = redisTemplate.keys(generalPattern);
            if (generalKeys != null && !generalKeys.isEmpty()) {
                redisTemplate.delete(generalKeys);
            }
            
            logger.debug("清除招募周期 {} 的简历缓存和相关查询缓存", cycleId);
        }
    }
}