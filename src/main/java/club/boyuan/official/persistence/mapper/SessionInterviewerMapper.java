package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.SessionInterviewer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 面试场次与面试官绑定 Mapper
 *
 * @author dhy
 */
public interface SessionInterviewerMapper extends BaseMapper<SessionInterviewer> {

    /**
     * 查某位面试官在指定周期内负责的所有场次ID，用于判定其在评价表中的可写范围。
     */
    @Select("SELECT si.session_id FROM session_interviewer si " +
            "JOIN interview_session s ON s.session_id = si.session_id " +
            "WHERE si.user_id = #{userId} AND s.cycle_id = #{cycleId}")
    List<Integer> selectSessionIdsByUserAndCycle(@Param("userId") Integer userId,
                                                 @Param("cycleId") Integer cycleId);
}
