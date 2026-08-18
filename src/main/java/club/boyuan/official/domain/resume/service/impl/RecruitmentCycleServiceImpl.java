package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.domain.resume.dto.OpenCycleDTO;
import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService;
import lombok.AllArgsConstructor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 招募周期服务实现类
 */
@Service
@AllArgsConstructor
public class RecruitmentCycleServiceImpl implements IRecruitmentCycleService {
    
    private static final Logger logger = LoggerFactory.getLogger(RecruitmentCycleServiceImpl.class);
    
    private final RecruitmentCycleMapper recruitmentCycleMapper;
    private final IResumeFieldDefinitionService fieldDefinitionService;
    private final SqlSessionFactory sqlSessionFactory;
    
    @Override
    public RecruitmentCycle createRecruitmentCycle(RecruitmentCycle recruitmentCycle) {
        logger.info("创建招募周期，名称: {}", recruitmentCycle.getCycleName());
        try {
            // 设置默认值
            if (recruitmentCycle.getStatus() == null) {
                recruitmentCycle.setStatus(1); // 默认未开始
            }
            if (recruitmentCycle.getIsActive() == null) {
                recruitmentCycle.setIsActive(1); // 默认启用
            }
            
            recruitmentCycleMapper.insert(recruitmentCycle);
            logger.info("招募周期创建成功，ID: {}", recruitmentCycle.getCycleId());
            return recruitmentCycle;
        } catch (DuplicateKeyException e) {
            logger.warn("创建招募周期失败，学术年份已存在: {}", recruitmentCycle.getAcademicYear());
            throw new IllegalArgumentException("学术年份已存在: " + recruitmentCycle.getAcademicYear());
        } catch (Exception e) {
            logger.error("创建招募周期失败，名称: {}", recruitmentCycle.getCycleName(), e);
            throw e;
        }
    }
    
    @Override
    public RecruitmentCycle updateRecruitmentCycle(RecruitmentCycle recruitmentCycle) {
        logger.info("更新招募周期，ID: {}", recruitmentCycle.getCycleId());
        try {
            RecruitmentCycle existingCycle = recruitmentCycleMapper.findById(recruitmentCycle.getCycleId());
            if (existingCycle == null) {
                logger.warn("尝试更新不存在的招募周期，ID: {}", recruitmentCycle.getCycleId());
                throw new IllegalArgumentException("招募周期不存在");
            }
            
            recruitmentCycleMapper.updateById(recruitmentCycle);
            logger.info("招募周期更新成功，ID: {}", recruitmentCycle.getCycleId());
            return recruitmentCycle;
        } catch (Exception e) {
            logger.error("更新招募周期失败，ID: {}", recruitmentCycle.getCycleId(), e);
            throw e;
        }
    }
    
    @Override
    @Transactional
    public void deleteRecruitmentCycle(Integer cycleId) {
        logger.info("删除招募周期，ID: {}", cycleId);
        try {
            RecruitmentCycle existingCycle = recruitmentCycleMapper.findById(cycleId);
            if (existingCycle == null) {
                logger.warn("尝试删除不存在的招募周期，ID: {}", cycleId);
                throw new IllegalArgumentException("招募周期不存在");
            }
            
            recruitmentCycleMapper.deleteById(cycleId);
            logger.info("招募周期删除成功，ID: {}", cycleId);
        } catch (Exception e) {
            logger.error("删除招募周期失败，ID: {}", cycleId, e);
            throw e;
        }
    }
    
    @Override
    public RecruitmentCycle getRecruitmentCycleById(Integer cycleId) {
        logger.debug("根据ID获取招募周期，ID: {}", cycleId);
        try {
            return recruitmentCycleMapper.findById(cycleId);
        } catch (Exception e) {
            logger.error("根据ID获取招募周期失败，ID: {}", cycleId, e);
            throw e;
        }
    }
    
    @Override
    public List<RecruitmentCycle> getAllRecruitmentCycles() {
        logger.debug("获取所有招募周期");
        try {
            return recruitmentCycleMapper.findAll();
        } catch (Exception e) {
            logger.error("获取所有招募周期失败", e);
            throw e;
        }
    }
    
    @Override
    public List<RecruitmentCycle> getRecruitmentCyclesByStatus(Integer status) {
        logger.debug("根据状态获取招募周期，状态: {}", status);
        try {
            return recruitmentCycleMapper.findByStatus(status);
        } catch (Exception e) {
            logger.error("根据状态获取招募周期失败，状态: {}", status, e);
            throw e;
        }
    }
    
    @Override
    public List<RecruitmentCycle> getRecruitmentCyclesByIsActive(Integer isActive) {
        logger.debug("根据是否启用获取招募周期，是否启用: {}", isActive);
        try {
            return recruitmentCycleMapper.findByIsActive(isActive);
        } catch (Exception e) {
            logger.error("根据是否启用获取招募周期失败，是否启用: {}", isActive, e);
            throw e;
        }
    }
    
    @Override
    public RecruitmentCycle getRecruitmentCycleByAcademicYear(String academicYear) {
        logger.debug("根据学年获取招募周期，学年: {}", academicYear);
        try {
            return recruitmentCycleMapper.findByAcademicYear(academicYear);
        } catch (Exception e) {
            logger.error("根据学年获取招募周期失败，学年: {}", academicYear, e);
            throw e;
        }
    }
    
    @Override
    @Transactional
    public void deleteRecruitmentCycles(List<Integer> cycleIds) {
        logger.info("批量删除招募周期，IDs: {}", cycleIds);
        try {
            if (cycleIds == null || cycleIds.isEmpty()) {
                logger.warn("尝试批量删除招募周期，但ID列表为空");
                return;
            }
            
            int deletedCount = recruitmentCycleMapper.batchDelete(cycleIds);
            logger.info("批量删除招募周期完成，删除数量: {}", deletedCount);
        } catch (Exception e) {
            logger.error("批量删除招募周期失败，IDs: {}", cycleIds, e);
            throw e;
        }
    }
    
    @Override
    public List<OpenCycleDTO> getOpenCyclesForApplication() {
        LocalDate today = LocalDate.now();
        List<RecruitmentCycle> open = recruitmentCycleMapper.findOpenForApplication(today);
        logger.debug("当前开放投递的周期数: {}，日期: {}", open.size(), today);
        return open.stream()
                .map(c -> {
                    int fieldCount = 0;
                    try {
                        fieldCount = fieldDefinitionService.getFieldDefinitionsByCycleId(c.getCycleId()).size();
                    } catch (Exception e) {
                        // 字段数只用于提示「该周期未配置表单」，取不到不该让整个列表失败
                        logger.warn("统计周期{}的简历字段数失败: {}", c.getCycleId(), e.getMessage());
                    }
                    return new OpenCycleDTO(c, fieldCount);
                })
                .toList();
    }

    @Override
    public void updateRecruitmentCycleStatusesBasedOnDate(LocalDate currentDate) {
        logger.info("根据当前日期更新招募周期状态，当前日期: {}", currentDate);
        try {
            int updatedCount = recruitmentCycleMapper.updateStatusBasedOnDate(currentDate);
            logger.info("根据当前日期更新招募周期状态完成，更新数量: {}", updatedCount);
        } catch (Exception e) {
            logger.error("根据当前日期更新招募周期状态失败，当前日期: {}", currentDate, e);
            throw e;
        }
    }
    
    @Override
    @Transactional
    public void updateRecruitmentCycles(List<RecruitmentCycle> recruitmentCycles) {
        logger.info("批量更新招募周期，数量: {}", recruitmentCycles.size());
        try {
            if (recruitmentCycles == null || recruitmentCycles.isEmpty()) {
                logger.warn("尝试批量更新招募周期，但列表为空");
                return;
            }

            // 使用批处理执行器进行批量更新
            try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                RecruitmentCycleMapper batchMapper = sqlSession.getMapper(RecruitmentCycleMapper.class);
                
                for (RecruitmentCycle cycle : recruitmentCycles) {
                    batchMapper.updateById(cycle);
                }
                
                sqlSession.commit();
                sqlSession.clearCache();
            }
            
            logger.info("批量更新招募周期完成");
        } catch (Exception e) {
            logger.error("批量更新招募周期失败，数量: {}", recruitmentCycles.size(), e);
            throw e;
        }
    }
    
    @Override
    public PageResultDTO<RecruitmentCycle> getAllRecruitmentCyclesWithPagination(int page, int size, String sortBy, String sortOrder) {
        logger.debug("分页获取所有招募周期，页码: {}, 大小: {}, 排序字段: {}, 排序顺序: {}", page, size, sortBy, sortOrder);
        try {
            // 将Java字段名转换为数据库列名
            String dbSortBy = convertFieldNameToColumnName(sortBy);
            
            int offset = page * size;
            List<RecruitmentCycle> cycles = recruitmentCycleMapper.findAllWithPaginationAndSorting(offset, size, dbSortBy, sortOrder);
            long totalElements = recruitmentCycleMapper.countByConditions(null, null, null, null);
            int totalPages = (int) Math.ceil((double) totalElements / size);
            
            return new PageResultDTO<>(cycles, totalElements, totalPages, page, size, page == 0, page >= totalPages - 1);
        } catch (Exception e) {
            logger.error("分页获取所有招募周期失败，页码: {}, 大小: {}, 排序字段: {}, 排序顺序: {}", page, size, sortBy, sortOrder, e);
            throw e;
        }
    }
    
    @Override
    public PageResultDTO<RecruitmentCycle> getRecruitmentCyclesByConditions(String cycleName, String academicYear, 
                                                                           Integer status, Integer isActive,
                                                                           int page, int size, String sortBy, String sortOrder) {
        logger.debug("根据条件分页查询招募周期，名称: {}, 学年: {}, 状态: {}, 是否启用: {}, 页码: {}, 大小: {}, 排序字段: {}, 排序顺序: {}", 
                cycleName, academicYear, status, isActive, page, size, sortBy, sortOrder);
        try {
            // 将Java字段名转换为数据库列名
            String dbSortBy = convertFieldNameToColumnName(sortBy);
            
            int offset = page * size;
            List<RecruitmentCycle> cycles = recruitmentCycleMapper.findByConditions(cycleName, academicYear, status, isActive, offset, size, dbSortBy, sortOrder);
            long totalElements = recruitmentCycleMapper.countByConditions(cycleName, academicYear, status, isActive);
            int totalPages = (int) Math.ceil((double) totalElements / size);
            
            return new PageResultDTO<>(cycles, totalElements, totalPages, page, size, page == 0, page >= totalPages - 1);
        } catch (Exception e) {
            logger.error("根据条件分页查询招募周期失败，名称: {}, 学年: {}, 状态: {}, 是否启用: {}, 页码: {}, 大小: {}, 排序字段: {}, 排序顺序: {}", 
                    cycleName, academicYear, status, isActive, page, size, sortBy, sortOrder, e);
            throw e;
        }
    }
    
    /**
     * 将Java字段名转换为数据库列名
     * @param fieldName Java字段名
     * @return 数据库列名
     */
    private String convertFieldNameToColumnName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        
        switch (fieldName) {
            case "cycleId":
                return "cycle_id";
            case "cycleName":
                return "cycle_name";
            case "startDate":
                return "start_date";
            case "endDate":
                return "end_date";
            case "academicYear":
                return "academic_year";
            case "isActive":
                return "is_active";
            case "createdAt":
                return "created_at";
            case "updatedAt":
                return "updated_at";
            default:
                return fieldName;
        }
    }
}