package club.boyuan.official.persistence.mapper;

import club.boyuan.official.domain.evaluation.dto.CandidateRow;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EvaluationSubmissionMapper extends BaseMapper<EvaluationSubmission> {

    List<CandidateRow> selectCandidates(@Param("cycleId") Integer cycleId,
                                        @Param("deptId") Integer deptId,
                                        @Param("minScore") Integer minScore,
                                        @Param("maxScore") Integer maxScore,
                                        @Param("claimed") String claimed,
                                        @Param("sortBy") String sortBy,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countCandidates(@Param("cycleId") Integer cycleId,
                         @Param("deptId") Integer deptId,
                         @Param("minScore") Integer minScore,
                         @Param("maxScore") Integer maxScore,
                         @Param("claimed") String claimed);

    List<EvaluationSubmission> selectByUserId(@Param("userId") Integer userId);

    List<EvaluationSubmission> selectByGithub(@Param("githubUsername") String githubUsername);
}