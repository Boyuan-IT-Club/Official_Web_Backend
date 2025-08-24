package club.boyuan.official.service.impl;

import club.boyuan.official.entity.RecruitmentCycle;
import club.boyuan.official.mapper.RecruitmentCycleMapper;
import club.boyuan.official.service.IRecruitmentCycleService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 招募周期服务实现类
 */
@Service
@AllArgsConstructor
public class RecruitmentCycleServiceImpl implements IRecruitmentCycleService {
    
    private static final Logger logger = LoggerFactory.getLogger(RecruitmentCycleServiceImpl.class);
    
    private final RecruitmentCycleMapper recruitmentCycleMapper;
    
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
            
            recruitmentCycleMapper.update(recruitmentCycle);
            logger.info("招募周期更新成功，ID: {}", recruitmentCycle.getCycleId());
            return recruitmentCycle;
        } catch (Exception e) {
            logger.error("更新招募周期失败，ID: {}", recruitmentCycle.getCycleId(), e);
            throw e;
        }
    }
    
    @Override
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
}