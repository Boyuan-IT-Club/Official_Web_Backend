-- KEYS[1] remain key  KEYS[2] user cycle lock key
-- ARGV[1] requestId  ARGV[2] lock ttl seconds
local remain = redis.call('GET', KEYS[1])
if remain == false then
    return -1
end
remain = tonumber(remain)
if remain <= 0 then
    return 0
end
if redis.call('EXISTS', KEYS[2]) == 1 then
    return -2
end
redis.call('DECRBY', KEYS[1], 1)
redis.call('SET', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[2]))
return 1
