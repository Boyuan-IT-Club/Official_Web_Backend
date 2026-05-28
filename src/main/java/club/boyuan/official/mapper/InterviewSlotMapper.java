package club.boyuan.official.mapper;

import club.boyuan.official.entity.InterviewSlot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 面试时段配置表 Mapper 接口
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
public interface InterviewSlotMapper extends BaseMapper<InterviewSlot> {

    InterviewSlot selectByIdForUpdate(@Param("slotId") Integer slotId);

    /**
     * 在仍有剩余名额时占用 1 个名额，并同步更新 status（1 可用 / 2 已满）。
     * @return 影响行数，1 表示成功
     */
    int occupyOneIfAvailable(@Param("slotId") Integer slotId);

    /** 释放 1 个名额（不低于 0），并刷新 status */
    int releaseOne(@Param("slotId") Integer slotId);
}
