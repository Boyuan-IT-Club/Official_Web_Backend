package club.boyuan.official.service.impl;

import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.AwardExperienceMapper;
import club.boyuan.official.service.IAwardExperienceService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AwardExperienceServiceImpl implements IAwardExperienceService {

    private static final Logger logger = LoggerFactory.getLogger(AwardExperienceServiceImpl.class);

    private final AwardExperienceMapper awardExperienceMapper;

    @Override
    public AwardExperience create(AwardExperience awardExperience) {
        logger.info("开始创建获奖经历，用户ID: {}", awardExperience.getUserId());
        logger.debug("获奖经历详情: awardName={}, awardTime={}, description={}", 
                    awardExperience.getAwardName(), awardExperience.getAwardTime(), awardExperience.getDescription());
        
        int result = awardExperienceMapper.save(awardExperience);
        if (result <= 0) {
            logger.error("创建获奖经历失败，用户ID: {}", awardExperience.getUserId());
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR);
        }
        
        logger.info("成功创建获奖经历，获奖ID: {}, 用户ID: {}", awardExperience.getAwardId(), awardExperience.getUserId());
        return awardExperience;
    }

    @Override
    public AwardExperience getById(Integer id) {
        logger.debug("开始根据ID获取获奖经历，获奖ID: {}", id);
        AwardExperience awardExperience = awardExperienceMapper.selectById(id);
        if (awardExperience == null) {
            logger.warn("未找到指定的获奖经历，获奖ID: {}", id);
        } else {
            logger.debug("成功获取获奖经历，获奖ID: {}", id);
        }
        return awardExperience;
    }

    @Override
    public List<AwardExperience> getByUserId(Integer userId) {
        logger.info("开始获取用户的所有获奖经历，用户ID: {}", userId);
        List<AwardExperience> awards = awardExperienceMapper.selectByUserId(userId);
        logger.info("成功获取用户的所有获奖经历，用户ID: {}, 数量: {}", userId, awards.size());
        return awards;
    }

    @Override
    public AwardExperience update(AwardExperience awardExperience) {
        logger.info("开始更新获奖经历，获奖ID: {}", awardExperience.getAwardId());
        logger.debug("更新的获奖经历详情: awardName={}, awardTime={}, description={}", 
                    awardExperience.getAwardName(), awardExperience.getAwardTime(), awardExperience.getDescription());
        
        AwardExperience existingAward = awardExperienceMapper.selectById(awardExperience.getAwardId());
        if (existingAward == null) {
            logger.warn("尝试更新不存在的获奖经历，获奖ID: {}", awardExperience.getAwardId());
            throw new BusinessException(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND);
        }
        
        int result = awardExperienceMapper.update(awardExperience);
        if (result <= 0) {
            logger.error("更新获奖经历失败，获奖ID: {}", awardExperience.getAwardId());
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR);
        }
        
        logger.info("成功更新获奖经历，获奖ID: {}", awardExperience.getAwardId());
        return awardExperience;
    }

    @Override
    public void deleteById(Integer id) {
        logger.info("开始删除获奖经历，获奖ID: {}", id);
        
        AwardExperience awardExperience = awardExperienceMapper.selectById(id);
        if (awardExperience == null) {
            logger.warn("尝试删除不存在的获奖经历，获奖ID: {}", id);
            throw new BusinessException(BusinessExceptionEnum.AWARD_EXPERIENCE_NOT_FOUND);
        }
        
        awardExperienceMapper.deleteById(id);
        logger.info("成功删除获奖经历，获奖ID: {}", id);
    }
}