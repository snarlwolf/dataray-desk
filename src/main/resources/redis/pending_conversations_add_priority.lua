-- 将会话 id 加入待分配队列队首（去重）：SET 不存在则 LPUSH 到 list 头部，优先被弹出；已存在返回 0
-- KEYS[1] = pending set, KEYS[2] = pending list
-- ARGV[1] = conversationId
local added = redis.call('SADD', KEYS[1], ARGV[1])
if added == 1 then redis.call('LPUSH', KEYS[2], ARGV[1]) end
return added
