-- 滑动窗口限流（ZSET）
-- KEYS[1] 限流 key
-- ARGV[1] 当前时间戳（毫秒）  ARGV[2] 窗口大小（毫秒）  ARGV[3] 窗口内最大请求数
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
local count = redis.call('ZCARD', KEYS[1])
if count >= limit then
    return 0
end
redis.call('ZADD', KEYS[1], now, now)
redis.call('PEXPIRE', KEYS[1], window)
return 1
