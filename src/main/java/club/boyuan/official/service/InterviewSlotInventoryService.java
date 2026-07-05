package club.boyuan.official.service;

/**
 * 面试时段库存（名额）统一写入口，供预约、改期、取消共用。
 */
public interface InterviewSlotInventoryService {

    /**
     * 原子占 1 个名额；成功返回 true。
     * 须在调用方已开启的事务内执行（与 schedule 写入同事务）。
     */
    boolean tryOccupy(Integer slotId);

    /** 释放 1 个名额 */
    void release(Integer slotId);

    /**
     * 改期：从旧时段挪 1 个名额到新时段（按 slotId 排序加行锁，避免死锁）。
     * @return 新时段占坑是否成功；失败时会尝试恢复旧时段
     */
    boolean transferOccupancy(Integer fromSlotId, Integer toSlotId);

    /** 将周期下各 slot 剩余名额预热到 Redis，供高并发快速拒绝 */
    void warmRemainCacheForCycle(Iterable<Integer> slotIds);

    /** 从 DB 刷新单个 slot 的 Redis 剩余名额 */
    void syncRemainCacheFromDb(Integer slotId);

    /**
     * 用调用方已持有的 slot 快照刷新 Redis 剩余名额，避免对同一行重复 SELECT。
     * 仅适用于调用方已在同一事务内拿到该 slot 最新状态的场景（如秒杀落库）。
     */
    void syncRemainCacheFromDb(club.boyuan.official.entity.InterviewSlot slot);

    /**
     * 仅 DB 原子占坑（不修改 Redis）。用于秒杀消费者：Redis 已在 Lua 中预扣。
     */
    boolean tryOccupyDbOnly(Integer slotId);

    /**
     * 仅 DB 释放名额（不修改 Redis）。取消预约等同步路径仍用 {@link #release(Integer)}。
     */
    void releaseDbOnly(Integer slotId);
}
