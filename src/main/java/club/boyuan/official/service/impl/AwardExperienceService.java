package club.boyuan.official.service.impl;

import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.mapper.AwardExperienceMapper;
import club.boyuan.official.service.IAwardExperienceService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AwardExperienceService implements IAwardExperienceService {

    private final AwardExperienceMapper awardExperienceMapper;

    @Override
    public AwardExperience create(AwardExperience awardExperience) {
        awardExperienceMapper.save(awardExperience);
        return awardExperience;
    }

    @Override
    public AwardExperience getById(Integer id) {
        return awardExperienceMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("获奖经历不存在"));
    }

    @Override
    public List<AwardExperience> getByUserId(Integer userId) {
        return awardExperienceMapper.findByUserId(userId);
    }

    @Override
    public AwardExperience update(AwardExperience awardExperience) {
        // 检查获奖经历是否存在
        if (!awardExperienceMapper.existsById(awardExperience.getAwardId())) {
            throw new IllegalArgumentException("获奖经历不存在");
        }
        int rowsAffected = awardExperienceMapper.update(awardExperience);
        if (rowsAffected <= 0) {
            throw new RuntimeException("更新获奖经历失败");
        }
        return awardExperience;
    }

    @Override
    public void deleteById(Integer id) {
        if (!awardExperienceMapper.existsById(id)) {
            throw new IllegalArgumentException("获奖经历不存在");
        }
        awardExperienceMapper.deleteById(id);
    }
}