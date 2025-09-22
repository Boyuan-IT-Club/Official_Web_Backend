package club.boyuan.official.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redis工具类，提供缓存管理功能
 */
@Component
public class RedisUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 清理所有简历字段定义缓存
     * 用于解决序列化兼容性问题
     */
    public void clearFieldDefinitionCache() {
        try {
            String pattern = "field_definition:*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("成功清理 {} 个字段定义缓存", keys.size());
            } else {
                logger.info("没有找到需要清理的字段定义缓存");
            }
        } catch (Exception e) {
            logger.error("清理字段定义缓存失败", e);
        }
    }
    
    /**
     * 清理所有缓存（慎用）
     */
    public void clearAllCache() {
        try {
            Set<String> keys = redisTemplate.keys("*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("成功清理所有缓存，共 {} 个key", keys.size());
            } else {
                logger.info("没有找到需要清理的缓存");
            }
        } catch (Exception e) {
            logger.error("清理所有缓存失败", e);
        }
    }
    
    /**
     * 清理特定模式的缓存
     * @param pattern 缓存key模式
     */
    public void clearCacheByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("成功清理模式 '{}' 的缓存，共 {} 个key", pattern, keys.size());
            } else {
                logger.info("没有找到模式 '{}' 的缓存", pattern);
            }
        } catch (Exception e) {
            logger.error("清理模式 '{}' 的缓存失败", pattern, e);
        }
    }
}