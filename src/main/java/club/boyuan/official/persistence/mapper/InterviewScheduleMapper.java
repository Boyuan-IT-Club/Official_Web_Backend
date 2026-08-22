package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.InterviewSchedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 面试安排表 Mapper 接口
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
public interface InterviewScheduleMapper extends BaseMapper<InterviewSchedule> {

    /**
     * 候选档案列表：收录「至少一条面试安排」的用户，附带最近面试时间、地点、所属周期名。
     * 一次 JOIN 聚合，避免 N+1。
     */
    List<Map<String, Object>> selectCandidateProfileRows(@Param("cycleId") Integer cycleId);
}
