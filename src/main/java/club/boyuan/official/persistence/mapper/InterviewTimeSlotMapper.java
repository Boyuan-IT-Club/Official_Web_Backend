package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 面试时间窗 Mapper
 *
 * @author dhy
 */
public interface InterviewTimeSlotMapper extends BaseMapper<InterviewTimeSlot> {
    @org.apache.ibatis.annotations.Update("""
            UPDATE interview_preference p SET accepted_time_slot_ids = JSON_REMOVE(accepted_time_slot_ids,
                (SELECT CONCAT('$[',t.idx-1,']') FROM JSON_TABLE(p.accepted_time_slot_ids,'$[*]'
                    COLUMNS (idx FOR ORDINALITY, slot_id INT PATH '$')) t WHERE t.slot_id=#{slotId}))
            WHERE JSON_CONTAINS(accepted_time_slot_ids,CAST(#{slotId} AS JSON))
            """)
    int removeFromPreferences(@org.apache.ibatis.annotations.Param("slotId") Integer slotId);
}
