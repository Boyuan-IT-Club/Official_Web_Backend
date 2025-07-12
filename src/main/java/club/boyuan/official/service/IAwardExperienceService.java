package club.boyuan.official.service;

import club.boyuan.official.entity.AwardExperience;
import java.util.List;

public interface IAwardExperienceService {
    // 创建获奖经历
    AwardExperience create(AwardExperience awardExperience);
    
    // 根据ID获取获奖经历
    AwardExperience getById(Integer id);
    
    // 根据用户ID获取所有获奖经历
    List<AwardExperience> getByUserId(Integer userId);
    
    // 更新获奖经历
    AwardExperience update(AwardExperience awardExperience);
    
    // 删除获奖经历
    void deleteById(Integer id);
}