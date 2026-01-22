package club.boyuan.official.mapper;

import club.boyuan.official.entity.AwardExperience;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AwardExperienceMapper extends BaseMapper<AwardExperience> {
    
    AwardExperience selectById(Integer awardId);

    List<AwardExperience> findByUserId(@Param("userId") Integer userId);

    List<AwardExperience> selectByUserId(Integer userId);

    int update(AwardExperience awardExperience);

    void deleteById(@Param("id") Integer id);

    void deleteAwardsByUserId(@Param("userId") Integer userId);

    List<AwardExperience> searchAwards(@Param("keyword") String keyword);
}