package club.boyuan.official.seckill;

import club.boyuan.official.config.InterviewBookingSeckillProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 秒杀库存：Redis Lua 原子预扣 / 回滚。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewBookingLuaInventoryService {

    private final StringRedisTemplate stringRedisTemplate;
    private final InterviewBookingSeckillProperties seckillProperties;

    private DefaultRedisScript<Long> occupyScript;
    private DefaultRedisScript<Long> rollbackScript;

    @PostConstruct
    void initScripts() {
        occupyScript = new DefaultRedisScript<>();
        occupyScript.setResultType(Long.class);
        occupyScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/lua/occupy_remain.lua")));

        rollbackScript = new DefaultRedisScript<>();
        rollbackScript.setResultType(Long.class);
        rollbackScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/lua/rollback_remain.lua")));
    }

    /**
     * Lua 原子预扣 1 个名额，并设置用户周期锁。
     */
    public SeckillLuaResult preDeduct(Integer slotId, Integer userId, Integer cycleId, String requestId) {
        Long result = stringRedisTemplate.execute(
                occupyScript,
                List.of(
                        InterviewBookingRedisKeys.remain(slotId),
                        InterviewBookingRedisKeys.userCycleLock(userId, cycleId)
                ),
                requestId,
                String.valueOf(seckillProperties.getUserLockTtlSeconds())
        );
        long code = result == null ? -1L : result;
        log.debug("Lua 预扣结果 slotId={}, userId={}, requestId={}, code={}", slotId, userId, requestId, code);
        return SeckillLuaResult.fromCode(code);
    }

    /**
     * 落库失败时回滚 Redis 预扣并释放用户锁。
     */
    public void rollbackPreDeduct(Integer slotId, Integer userId, Integer cycleId) {
        stringRedisTemplate.execute(
                rollbackScript,
                List.of(
                        InterviewBookingRedisKeys.remain(slotId),
                        InterviewBookingRedisKeys.userCycleLock(userId, cycleId)
                )
        );
        log.debug("Lua 回滚预扣 slotId={}, userId={}, cycleId={}", slotId, userId, cycleId);
    }

    public void clearUserCycleLock(Integer userId, Integer cycleId) {
        stringRedisTemplate.delete(InterviewBookingRedisKeys.userCycleLock(userId, cycleId));
    }
}
