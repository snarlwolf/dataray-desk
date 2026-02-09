package com.skydawn.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 客服台 Redis 写入/更新/删除（desk:cs / desk:uq）
 * TTL：仅 user-offline-since 24h、锁 30s。会话相关 key 不设 TTL，由客服手动结束会话时统一删除。
 */
public class RedisOperation {

    /** user-offline-since 键 TTL（小时） */
    public static final int TTL_USER_OFFLINE_HOURS = 24;
    public static final int TTL_LOCK_SECONDS = 30;

    private final StringRedisTemplate redis;

    public RedisOperation(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void setUsers(String userId, String logintime) {
        if (userId == null || userId.isBlank() || logintime == null) return;
        String key = CsRedisKeys.users(Objects.requireNonNull(userId));
        redis.opsForValue().set(Objects.requireNonNull(key), Objects.requireNonNull(logintime));
    }

    public void deleteUsers(String userId) {
        if (userId == null || userId.isBlank()) return;
        String key = CsRedisKeys.users(Objects.requireNonNull(userId));
        redis.delete(Objects.requireNonNull(key));
    }

    public void setUserOfflineSince(String userId, String timestamp) {
        if (userId == null || userId.isBlank() || timestamp == null) return;
        String key = CsRedisKeys.userOfflineSince(Objects.requireNonNull(userId));
        redis.opsForValue().set(Objects.requireNonNull(key), Objects.requireNonNull(timestamp), Objects.requireNonNull(Duration.ofHours(TTL_USER_OFFLINE_HOURS)));
    }

    public void deleteUserOfflineSince(String userId) {
        if (userId == null || userId.isBlank()) return;
        String key = CsRedisKeys.userOfflineSince(Objects.requireNonNull(userId));
        redis.delete(Objects.requireNonNull(key));
    }

    /** 仅当 key 不存在时初始化为空 Set（占位 add 后 remove，返回是否新建） */
    public boolean initUserConversationIfAbsent(String userId) {
        if (userId == null || userId.isBlank()) return false;
        String key = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        Long added = redis.opsForSet().add(Objects.requireNonNull(key), "__init__");
        if (added != null && added.longValue() == 1L) {
            redis.opsForSet().remove(key, "__init__");
        }
        return Long.valueOf(1L).equals(added);
    }

    public void userConversationAdd(String userId, String conversationId) {
        if (userId == null || userId.isBlank() || conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        redis.opsForSet().add(Objects.requireNonNull(key), Objects.requireNonNull(conversationId));
    }

    public void userConversationRemove(String userId, String conversationId) {
        if (userId == null || userId.isBlank() || conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        redis.opsForSet().remove(Objects.requireNonNull(key), Objects.requireNonNull(conversationId));
    }

    public void userConversationClear(String userId) {
        if (userId == null || userId.isBlank()) return;
        String key = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        redis.delete(Objects.requireNonNull(key));
    }

    public void setConversationUser(String conversationId, String userId) {
        if (conversationId == null || conversationId.isBlank() || userId == null || userId.isBlank()) return;
        String key = CsRedisKeys.conversationUser(Objects.requireNonNull(conversationId));
        redis.opsForValue().set(Objects.requireNonNull(key), Objects.requireNonNull(userId));
    }

    public void deleteConversationUser(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.conversationUser(Objects.requireNonNull(conversationId));
        redis.delete(Objects.requireNonNull(key));
    }

    public void setConversationType(String conversationId, String type) {
        if (conversationId == null || conversationId.isBlank() || type == null || type.isBlank()) return;
        String key = CsRedisKeys.conversationType(Objects.requireNonNull(conversationId));
        redis.opsForValue().set(Objects.requireNonNull(key), Objects.requireNonNull(type));
    }

    public void setConversationPhone(String conversationId, String phoneNumberId) {
        if (conversationId == null || conversationId.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) return;
        String key = CsRedisKeys.conversationPhone(Objects.requireNonNull(conversationId));
        redis.opsForValue().set(Objects.requireNonNull(key), Objects.requireNonNull(phoneNumberId));
    }

    public void deleteConversationType(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.conversationType(Objects.requireNonNull(conversationId));
        redis.delete(Objects.requireNonNull(key));
    }

    /** Redis 会话 id -> 库表 conversation.id，供消息入库时解析 conversation_id（不存 conversation 表，仅缓存） */
    public Long getConversationDbId(String redisConversationId) {
        if (redisConversationId == null || redisConversationId.isBlank()) return null;
        String key = CsRedisKeys.conversationDbId(redisConversationId);
        String val = redis.opsForValue().get(key);
        if (val == null || val.isBlank()) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setConversationDbId(String redisConversationId, Long conversationDbId) {
        if (redisConversationId == null || redisConversationId.isBlank() || conversationDbId == null) return;
        String key = CsRedisKeys.conversationDbId(redisConversationId);
        redis.opsForValue().set(key, String.valueOf(conversationDbId));
    }

    public void deleteConversationPhone(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.conversationPhone(Objects.requireNonNull(conversationId));
        redis.delete(Objects.requireNonNull(key));
    }

    /** 单会话消息列表最大条数，超出时保留最近一段 */
    public static final int CONVERSATION_MESSAGES_MAX = 1000;

    /** 将会话消息 JSON 追加到 LIST，LTRIM 保留最近 {@value #CONVERSATION_MESSAGES_MAX} 条；会话由客服手动结束时删除。 */
    public void appendConversationMessage(String conversationId, String messageJson) {
        if (conversationId == null || conversationId.isBlank() || messageJson == null) return;
        String key = CsRedisKeys.conversationMessages(Objects.requireNonNull(conversationId));
        redis.opsForList().rightPush(Objects.requireNonNull(key), Objects.requireNonNull(messageJson));
        redis.opsForList().trim(key, -CONVERSATION_MESSAGES_MAX, -1);
    }

    /** 结束会话时删除会话消息列表 */
    public void deleteConversationMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.conversationMessages(Objects.requireNonNull(conversationId));
        redis.delete(Objects.requireNonNull(key));
    }

    /** 分布式锁：SET key value NX EX 30，成功返回 true */
    public boolean tryLock(String lockKey, String value) {
        if (lockKey == null || value == null) return false;
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(Objects.requireNonNull(lockKey), Objects.requireNonNull(value), Objects.requireNonNull(Duration.ofSeconds(TTL_LOCK_SECONDS))));
    }

    public void unlock(String lockKey) {
        if (lockKey == null) return;
        redis.delete(Objects.requireNonNull(lockKey));
    }

    /** 结束会话：删除 user-conversation 成员、conversation-user、type、phone、messages，并负载 ZSET -1 */
    public void endSessionAtomic(String fromId, String userId, String conversationId) {
        if (fromId == null || userId == null || conversationId == null) return;
        String userConvKey = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        String convUserKey = CsRedisKeys.conversationUser(Objects.requireNonNull(conversationId));
        redis.opsForSet().remove(userConvKey, conversationId);
        redis.delete(convUserKey);
        deleteConversationType(conversationId);
        deleteConversationPhone(conversationId);
        deleteConversationMessages(conversationId);
        decrLoadZset(userId);
    }

    // ---------- 负载 ZSET（desk:cs:load:zset），用于选负载最小的客服 ----------
    /** 将客服加入负载 ZSET 或设置其当前负载（登录时用，score = 当前会话数） */
    public void setLoadZsetScore(String userId, int conversationCount) {
        if (userId == null || userId.isBlank()) return;
        redis.opsForZSet().add(CsRedisKeys.LOAD_ZSET, userId, conversationCount);
    }

    /** 负载 -1（结束会话或转移时从原客服移除） */
    public void decrLoadZset(String userId) {
        if (userId == null || userId.isBlank()) return;
        redis.opsForZSet().incrementScore(CsRedisKeys.LOAD_ZSET, userId, -1);
    }

    /** 负载 +1（仅转移时给目标客服加，分配时由 Lua 脚本统一加） */
    public void incrLoadZset(String userId) {
        if (userId == null || userId.isBlank()) return;
        redis.opsForZSet().incrementScore(CsRedisKeys.LOAD_ZSET, userId, 1);
    }

    /**
     * 原子将会话分配给指定客服（负载 &lt; maxCount 时执行 SADD、SET、ZINCRBY；会话 key 由结束会话时删除，不设 TTL）。
     * @return true 分配成功，false 该客服已达上限未分配
     */
    public boolean assignConversationToAgent(String userId, String conversationId, int maxCount) {
        if (userId == null || conversationId == null || maxCount <= 0) return false;
        String loadZset = CsRedisKeys.LOAD_ZSET;
        String userConvKey = CsRedisKeys.userConversation(userId);
        String convUserKey = CsRedisKeys.conversationUser(conversationId);
        Long result = redis.execute(
                CsRedisScripts.assignConversationToAgent(),
                List.of(loadZset, userConvKey, convUserKey),
                userId, conversationId, String.valueOf(maxCount));
        return result != null && result == 1L;
    }

    /** 将会话 id 加入待分配队列队尾（去重），返回是否新加入 */
    public boolean pendingConversationsAdd(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return false;
        Long added = redis.execute(CsRedisScripts.pendingAdd(),
                List.of(CsRedisKeys.PENDING_CONVERSATIONS_SET, CsRedisKeys.PENDING_CONVERSATIONS_LIST),
                conversationId);
        return added != null && added == 1L;
    }

    /** 将会话 id 加入待分配队列队首（优先出队，用于离线转移无目标时），去重，返回是否新加入 */
    public boolean pendingConversationsAddPriority(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return false;
        Long added = redis.execute(CsRedisScripts.pendingAddPriority(),
                List.of(CsRedisKeys.PENDING_CONVERSATIONS_SET, CsRedisKeys.PENDING_CONVERSATIONS_LIST),
                conversationId);
        return added != null && added == 1L;
    }

    /** 从待分配队列 FIFO 弹出最多 count 条，每次最多建议 5 条避免独占 */
    public List<String> pendingConversationsPopMulti(int count) {
        if (count <= 0) return List.of();
        List<String> list = redis.execute(CsRedisScripts.pendingPopMulti(),
                List.of(CsRedisKeys.PENDING_CONVERSATIONS_LIST, CsRedisKeys.PENDING_CONVERSATIONS_SET),
                String.valueOf(count));
        return list != null ? list : Collections.emptyList();
    }

    /** 发布消息到指定频道（用于多实例挤掉旧 WebSocket） */
    public void publish(String channel, String message) {
        if (channel == null || message == null) return;
        redis.convertAndSend(Objects.requireNonNull(channel), Objects.requireNonNull(message));
    }
}
