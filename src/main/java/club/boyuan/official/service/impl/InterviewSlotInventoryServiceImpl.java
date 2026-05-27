package club.boyuan.official.service.impl;

import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.mapper.InterviewSlotMapper;
import club.boyuan.official.service.InterviewSlotInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 面试时段名额库存：DB 条件更新为权威；Redis 缓存剩余名额用于高并发快速拒绝。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewSlotInventoryServiceImpl implements InterviewSlotInventoryService {

    private static final String REDIS_REMAIN_PREFIX = "interview:slot:remain:";
    private static final long REDIS_TTL_HOURS = 24;

    private final InterviewSlotMapper interviewSlotMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean tryOccupy(Integer slotId) {
        return doTryOccupy(slotId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void release(Integer slotId) {
        doRelease(slotId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transferOccupancy(Integer fromSlotId, Integer toSlotId) {
        if (Objects.equals(fromSlotId, toSlotId)) {
            return true;
        }
        lockSlotsInOrder(fromSlotId, toSlotId);
        doRelease(fromSlotId);
        if (doTryOccupy(toSlotId)) {
            return true;
        }
        log.warn("改期占新坑失败，尝试恢复旧 slot，from={}, to={}", fromSlotId, toSlotId);
        if (!doTryOccupy(fromSlotId)) {
            log.error("改期回滚失败，旧 slot 未能恢复占用，from={}", fromSlotId);
            syncRemainCacheFromDb(fromSlotId);
        }
        syncRemainCacheFromDb(toSlotId);
        return false;
    }

    @Override
    public void warmRemainCacheForCycle(Iterable<Integer> slotIds) {
        if (slotIds == null) {
            return;
        }
        for (Integer slotId : slotIds) {
            if (slotId != null) {
                syncRemainCacheFromDb(slotId);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean tryOccupyDbOnly(Integer slotId) {
        if (slotId == null) {
            return false;
        }
        int rows = interviewSlotMapper.occupyOneIfAvailable(slotId);
        if (rows > 0) {
            log.debug("DB 占坑成功（秒杀落库），slotId={}", slotId);
            return true;
        }
        log.debug("DB 占坑失败（秒杀落库），slotId={}", slotId);
        return false;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void releaseDbOnly(Integer slotId) {
        if (slotId == null) {
            return;
        }
        interviewSlotMapper.releaseOne(slotId);
        log.debug("DB 释放名额，slotId={}", slotId);
    }

    @Override
    public void syncRemainCacheFromDb(Integer slotId) {
        InterviewSlot slot = interviewSlotMapper.selectById(slotId);
        if (slot == null) {
            redisTemplate.delete(redisKey(slotId));
            return;
        }
        int max = slot.getMaxCapacity() == null ? 0 : slot.getMaxCapacity();
        int occupied = slot.getCurrentOccupied() == null ? 0 : slot.getCurrentOccupied();
        long remain = Math.max(0L, (long) max - occupied);
        redisTemplate.opsForValue().set(redisKey(slotId), remain, REDIS_TTL_HOURS, TimeUnit.HOURS);
    }

    private boolean doTryOccupy(Integer slotId) {
        if (slotId == null) {
            return false;
        }
        if (fastRejectByRedis(slotId)) {
            log.debug("Redis 快速拒绝占坑，slotId={}", slotId);
            return false;
        }
        int rows = interviewSlotMapper.occupyOneIfAvailable(slotId);
        if (rows > 0) {
            decrementRedisRemain(slotId);
            log.debug("DB 占坑成功，slotId={}", slotId);
            return true;
        }
        syncRemainCacheFromDb(slotId);
        log.debug("DB 占坑失败（已满或关闭），slotId={}", slotId);
        return false;
    }

    private void doRelease(Integer slotId) {
        if (slotId == null) {
            return;
        }
        interviewSlotMapper.releaseOne(slotId);
        incrementRedisRemain(slotId);
        log.debug("释放名额，slotId={}", slotId);
    }

    private void lockSlotsInOrder(Integer slotIdA, Integer slotIdB) {
        int first = Math.min(slotIdA, slotIdB);
        int second = Math.max(slotIdA, slotIdB);
        interviewSlotMapper.selectByIdForUpdate(first);
        if (second != first) {
            interviewSlotMapper.selectByIdForUpdate(second);
        }
    }

    private boolean fastRejectByRedis(Integer slotId) {
        Object cached = redisTemplate.opsForValue().get(redisKey(slotId));
        if (cached == null) {
            return false;
        }
        return parseLong(cached) <= 0;
    }

    private void decrementRedisRemain(Integer slotId) {
        String key = redisKey(slotId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            Long remain = redisTemplate.opsForValue().decrement(key);
            if (remain != null && remain < 0) {
                redisTemplate.opsForValue().set(key, 0L, REDIS_TTL_HOURS, TimeUnit.HOURS);
            }
        }
    }

    private void incrementRedisRemain(Integer slotId) {
        String key = redisKey(slotId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().increment(key);
        } else {
            syncRemainCacheFromDb(slotId);
        }
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String redisKey(Integer slotId) {
        return REDIS_REMAIN_PREFIX + slotId;
    }
}
