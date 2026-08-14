package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.InterviewEvaluation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;

/**
 * 面试评价 Mapper
 *
 * @author dhy
 */
public interface InterviewEvaluationMapper extends BaseMapper<InterviewEvaluation> {

    /**
     * 物化回写专用的原子 upsert：命中唯一键 (schedule_id, interviewer_user_id) 时更新。
     * <p>
     * 两点约束写在 SQL 里而不是 Java 里，是为了在并发物化时也成立：
     * 一是仅当传入 version 不低于库中 version 才覆盖，避免迟到的旧快照回退已保存的数据；
     * 二是不触碰 ai_suggestion —— 该列由 AI 面试助手写入，与协同表格的物化互不覆盖。
     *
     * @return 影响行数（插入为 1，更新为 2，无变化为 0）
     */
    @Insert("INSERT INTO interview_evaluation " +
            "(schedule_id, cycle_id, resume_id, interviewer_user_id, scores, total_score, comment, recommendation, status, version) " +
            "VALUES (#{scheduleId}, #{cycleId}, #{resumeId}, #{interviewerUserId}, #{scores}, #{totalScore}, " +
            "#{comment}, #{recommendation}, #{status}, #{version}) " +
            "ON DUPLICATE KEY UPDATE " +
            "scores = IF(VALUES(version) >= version, VALUES(scores), scores), " +
            "total_score = IF(VALUES(version) >= version, VALUES(total_score), total_score), " +
            "comment = IF(VALUES(version) >= version, VALUES(comment), comment), " +
            "recommendation = IF(VALUES(version) >= version, VALUES(recommendation), recommendation), " +
            "status = IF(VALUES(version) >= version, VALUES(status), status), " +
            "version = GREATEST(version, VALUES(version))")
    int upsertMaterialized(InterviewEvaluation evaluation);
}
