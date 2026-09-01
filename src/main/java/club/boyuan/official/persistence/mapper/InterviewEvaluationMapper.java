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
     * 物化回写专用的原子 upsert：命中唯一键 schedule_id 时更新（一场面试一份评价）。
     * <p>
     * 三点约束写在 SQL 里而不是 Java 里，是为了在并发物化时也成立：
     * 一是仅当传入 version 不低于库中 version 才覆盖，避免迟到的旧快照回退已保存的数据；
     * 二是不触碰 ai_suggestion —— 该列由 AI 面试助手写入，与协同表格的物化互不覆盖；
     * 三是 submitted_at 只在首次定稿时落一次，之后重复物化不会把定稿时间不断刷新。
     *
     * @return 影响行数（插入为 1，更新为 2，无变化为 0）
     */
    @Insert("INSERT INTO interview_evaluation " +
            "(schedule_id, cycle_id, resume_id, scores, dimension_notes, dimension_writers, total_score, " +
            "comment, recommendation, status, " +
            "contributors, last_edited_by, submitted_by, submitted_at, version) " +
            "VALUES (#{scheduleId}, #{cycleId}, #{resumeId}, #{scores}, #{dimensionNotes}, " +
            "#{dimensionWriters}, #{totalScore}, " +
            "#{comment}, #{recommendation}, #{status}, #{contributors}, #{lastEditedBy}, " +
            "#{submittedBy}, #{submittedAt}, #{version}) " +
            "ON DUPLICATE KEY UPDATE " +
            "scores = IF(VALUES(version) >= version, VALUES(scores), scores), " +
            "dimension_notes = IF(VALUES(version) >= version, VALUES(dimension_notes), dimension_notes), " +
            // 维度作者与 contributors 同理用 COALESCE 兜底：协同服务重启后这一轮带不出署名，
            // 此时保留库里已记录的，不要用 null 把它冲掉
            "dimension_writers = IF(VALUES(version) >= version, COALESCE(VALUES(dimension_writers), dimension_writers), dimension_writers), " +
            "total_score = IF(VALUES(version) >= version, VALUES(total_score), total_score), " +
            "comment = IF(VALUES(version) >= version, VALUES(comment), comment), " +
            "recommendation = IF(VALUES(version) >= version, VALUES(recommendation), recommendation), " +
            "status = IF(VALUES(version) >= version, VALUES(status), status), " +
            // 参与人在 Java 侧已与库中旧值并集过；后三者用 COALESCE 兜底：
            // 协同服务重启后 tracker 清空，这一轮物化带不出署名信息，此时保留库里已有的
            "contributors = IF(VALUES(version) >= version, VALUES(contributors), contributors), " +
            "last_edited_by = IF(VALUES(version) >= version, COALESCE(VALUES(last_edited_by), last_edited_by), last_edited_by), " +
            "submitted_by = IF(VALUES(version) >= version, COALESCE(VALUES(submitted_by), submitted_by), submitted_by), " +
            "submitted_at = COALESCE(submitted_at, VALUES(submitted_at)), " +
            "version = GREATEST(version, VALUES(version))")
    int upsertMaterialized(InterviewEvaluation evaluation);
}
