package club.boyuan.official.mapper;

import club.boyuan.official.entity.AwardExperience;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AwardExperienceMapper {
    Optional<AwardExperience> findById(@Param("id") Integer id);

    boolean existsById(@Param("id") Integer id);

    int save(AwardExperience awardExperience);

    int update(AwardExperience awardExperience);

    List<AwardExperience> findByUserId(@Param("userId") Integer userId);

    void deleteById(@Param("id") Integer id);

    void deleteAwardsByUserId(@Param("userId") Integer userId);
}