-- 从待分配队列 FIFO 弹出最多 n 条，并从 SET 中移除，返回弹出的 conversationId 列表
-- KEYS[1] = pending list, KEYS[2] = pending set
-- ARGV[1] = count (e.g. 5)
local result = {}
local count = tonumber(ARGV[1])
if count <= 0 then return result end
for i = 1, count do
  local cid = redis.call('LPOP', KEYS[1])
  if not cid then break end
  redis.call('SREM', KEYS[2], cid)
  table.insert(result, cid)
end
return result
