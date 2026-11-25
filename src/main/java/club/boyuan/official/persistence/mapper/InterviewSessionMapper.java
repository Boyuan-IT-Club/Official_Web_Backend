package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.InterviewSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 面试场次 Mapper
 *
 * @author dhy
 */
public interface InterviewSessionMapper extends BaseMapper<InterviewSession>, ParentRecordMode {

    @Select("SELECT cycle_id FROM recruitment_cycle WHERE cycle_id=#{cycleId} FOR UPDATE")
    Integer lockAllocationCycle(int cycleId);

    @Select("SELECT cycle_id FROM recruitment_cycle WHERE cycle_id=(SELECT cycle_id FROM resume WHERE resume_id=#{resumeId}) FOR UPDATE")
    Integer lockCycleForResume(int resumeId);

    @Select("SELECT cycle_id FROM recruitment_cycle WHERE cycle_id=(SELECT cycle_id FROM interview_schedule WHERE schedule_id=#{scheduleId}) FOR UPDATE")
    Integer lockCycleForSchedule(int scheduleId);

    @Select("SELECT resume_id FROM resume WHERE cycle_id=#{cycleId} AND status >= 2 AND status <> 5 ORDER BY resume_id FOR UPDATE")
    java.util.List<Integer> lockCandidates(int cycleId);

    @Select("SELECT session_id FROM interview_session WHERE cycle_id=#{cycleId} ORDER BY session_id FOR UPDATE")
    java.util.List<Integer> lockSessions(int cycleId);

    @Select("SELECT interview_time FROM interview_schedule WHERE session_id=#{sessionId} AND status=1 "
            + "AND interview_time IS NOT NULL AND (#{excludeId} IS NULL OR schedule_id!=#{excludeId}) ORDER BY schedule_id FOR UPDATE")
    java.util.List<java.time.LocalDateTime> lockOccupiedTimes(@Param("sessionId") int sessionId,
                                                             @Param("excludeId") Integer excludeId);

    /**
     * 在仍有剩余名额且状态可用时占用 1 个名额（原子操作）。
     *
     * @return 影响行数，1 表示占用成功，0 表示已满 / 关闭
     */
    @Update("UPDATE interview_session SET current_occupied = current_occupied + 1 " +
            "WHERE session_id = #{sessionId} AND status = 1 AND current_occupied < capacity")
    int occupyOneIfAvailable(@Param("sessionId") Integer sessionId);

    /**
     * 无视容量占用 1 个名额，用于管理员手动换场。
     *
     * 一键分配与人工调剂走 {@link #occupyOneIfAvailable}，满了就不塞；而手动调整
     * 是管理员明确要把某个人放进这一场（临时加座、两人背靠背），按容量拦下来反而
     * 挡了正事。超额后 current_occupied 可能大于 capacity，调用方应给出告警。
     */
    @Update("UPDATE interview_session SET current_occupied = current_occupied + 1 "
            + "WHERE session_id = #{sessionId}")
    int occupyOneIgnoringCapacity(@Param("sessionId") Integer sessionId);

    /**
     * 释放 1 个名额（不低于 0）。
     */
    @Update("UPDATE interview_session SET current_occupied = GREATEST(current_occupied - 1, 0) " +
            "WHERE session_id = #{sessionId}")
    int releaseOne(@Param("sessionId") Integer sessionId);
}
