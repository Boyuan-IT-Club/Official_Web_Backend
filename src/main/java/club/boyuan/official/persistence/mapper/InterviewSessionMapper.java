package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.InterviewSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 面试场次 Mapper
 *
 * @author dhy
 */
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

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
