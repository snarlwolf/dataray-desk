-- redis_conversation_id 仅保留在 message 表中（流转用），不入 conversation 表。
-- 若之前已在 conversation 表加过该列，执行下面语句删除（PostgreSQL）：
ALTER TABLE conversation DROP COLUMN IF EXISTS redis_conversation_id;
DROP INDEX IF EXISTS idx_conversation_redis_conv_id;
