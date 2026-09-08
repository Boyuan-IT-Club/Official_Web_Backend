package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.InterviewResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 面试结果表 Mapper 接口
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
public interface InterviewResultMapper extends BaseMapper<InterviewResult> {

    Page<InterviewResult> selectResultPage(Page<InterviewResult> pageInfo, Integer cycleId, String name, String decision, String department);

    /**
     * 过滤出确实属于该周期的 result_id，用于批量操作前的归属校验。
     */
    List<Integer> selectResultIdsInCycle(@Param("cycleId") Integer cycleId, @Param("resultIds") List<Integer> resultIds);

    /** 只返回该周期仍未发布决定（decision=0）的结果，用于加入预录取名单。 */
    List<Integer> selectPendingResultIdsInCycle(@Param("cycleId") Integer cycleId,
                                                @Param("resultIds") List<Integer> resultIds);
}
