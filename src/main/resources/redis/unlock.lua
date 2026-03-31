-- 安全释放分布式锁：仅当 key 的值与持有者 value 一致时才删除，防止误删他人锁。
-- KEYS[1]: lockKey
-- ARGV[1]: lockValue（持有者标识）
-- 返回 1=成功释放，0=key 不存在或 value 不匹配（锁已过期或被他人占用）
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end
