-- KEYS[1] remain key  KEYS[2] user cycle lock key
redis.call('INCRBY', KEYS[1], 1)
if KEYS[2] ~= nil and KEYS[2] ~= '' then
    redis.call('DEL', KEYS[2])
end
return 1
