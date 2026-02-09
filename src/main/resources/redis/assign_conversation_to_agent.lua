-- 原子分配会话给指定客服：校验负载 < maxCount，ZINCRBY 负载，SADD user-conversation，SET conversation-user（无 TTL，由结束会话时删除）
-- KEYS[1] = loadZset, KEYS[2] = user-conversation key, KEYS[3] = conversation-user key
-- ARGV[1] = userId, ARGV[2] = conversationId, ARGV[3] = maxCount
local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
if score then score = tonumber(score) else score = 0 end
if score >= tonumber(ARGV[3]) then return 0 end
redis.call('ZINCRBY', KEYS[1], 1, ARGV[1])
redis.call('SADD', KEYS[2], ARGV[2])
redis.call('SET', KEYS[3], ARGV[1])
return 1
