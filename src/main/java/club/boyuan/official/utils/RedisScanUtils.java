package club.boyuan.official.utils;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.util.HashSet;
import java.util.Set;

/**
 * Redis 键扫描工具，避免生产环境使用 {@code KEYS} 阻塞。
 */
public final class RedisScanUtils {

    private RedisScanUtils() {
    }

    public static void deleteByPattern(RedisTemplate<String, ?> redisTemplate, String pattern) {
        Set<String> keys = scanKeys(redisTemplate, pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public static Set<String> scanKeys(RedisTemplate<String, ?> redisTemplate, String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
