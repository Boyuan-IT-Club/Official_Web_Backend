package club.boyuan.official.service;

import club.boyuan.official.entity.InterviewSlot;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 在大时段 {@link InterviewSlot} 内按容量均分小时间段，并计算第 N 个预约的具体到场时间。
 * <p>
 * 小片段时长 = (end_time - start_time) / max_capacity；第 k 个占坑（1-based）对应索引 k-1。
 */
@Service
public class InterviewFineSlotTimeService {

    /**
     * 根据占坑后的 {@code current_occupied} 计算本次预约的精确 {@code interview_time}。
     */
    public LocalDateTime resolveForOccupiedSlot(InterviewSlot slot) {
        if (slot == null || slot.getCurrentOccupied() == null || slot.getCurrentOccupied() <= 0) {
            throw new IllegalStateException("无法分配小时间段：时段占用数无效 slotId="
                    + (slot != null ? slot.getSlotId() : null));
        }
        return resolveForSlotIndex(slot, slot.getCurrentOccupied() - 1);
    }

    /**
     * @param zeroBasedIndex 0 表示第一个小时间段，max_capacity-1 表示最后一个
     */
    public LocalDateTime resolveForSlotIndex(InterviewSlot slot, int zeroBasedIndex) {
        if (slot == null) {
            throw new IllegalStateException("无法分配小时间段：slot 为空");
        }
        if (slot.getInterviewDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            throw new IllegalStateException("无法分配小时间段：时段日期或时间未配置 slotId=" + slot.getSlotId());
        }
        Integer maxCapacity = slot.getMaxCapacity();
        if (maxCapacity == null || maxCapacity <= 0) {
            throw new IllegalStateException("无法分配小时间段：max_capacity 无效 slotId=" + slot.getSlotId());
        }
        if (zeroBasedIndex < 0 || zeroBasedIndex >= maxCapacity) {
            throw new IllegalStateException("无法分配小时间段：索引越界 slotId=" + slot.getSlotId()
                    + ", index=" + zeroBasedIndex + ", maxCapacity=" + maxCapacity);
        }

        LocalDateTime windowStart = LocalDateTime.of(slot.getInterviewDate(), slot.getStartTime());
        LocalDateTime windowEnd = LocalDateTime.of(slot.getInterviewDate(), slot.getEndTime());
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalStateException("无法分配小时间段：结束时间须晚于开始时间 slotId=" + slot.getSlotId());
        }

        long totalSeconds = Duration.between(windowStart, windowEnd).getSeconds();
        long sliceSeconds = totalSeconds / maxCapacity;
        return windowStart.plusSeconds(sliceSeconds * zeroBasedIndex);
    }
}
