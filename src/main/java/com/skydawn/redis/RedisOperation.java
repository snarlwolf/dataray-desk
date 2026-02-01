package com.skydawn.redis;

import java.time.Duration;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 客服台 Redis 写入/更新/删除（desk:cs）
 * TTL：user-offline-since 24h（自动过期，不堆积），锁 30s，message-queue 7d（可选）。
 * 登录/下线事件若需长期审计，可另行写入数据库日志。
 */
public class RedisOperation {

    /** user-offline-since 键 TTL（小时），过期自动删除，避免 Redis 数据无限增长 */
    public static final int TTL_USER_OFFLINE_HOURS = 24;
    public static final int TTL_LOCK_SECONDS = 30;
    public static final int TTL_MESSAGE_QUEUE_DAYS = 7;

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

    public void deleteConversationPhone(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.conversationPhone(Objects.requireNonNull(conversationId));
        redis.delete(Objects.requireNonNull(key));
    }

    /** 单会话消息列表最大条数，超出时保留最近一段 */
    public static final int CONVERSATION_MESSAGES_MAX = 1000;

    /** 将会话消息 JSON 追加到 LIST，并 LTRIM 保留最近 {@value #CONVERSATION_MESSAGES_MAX} 条 */
    public void appendConversationMessage(String conversationId, String messageJson) {
        if (conversationId == null || conversationId.isBlank() || messageJson == null) return;
        String key = CsRedisKeys.conversationMessages(Objects.requireNonNull(conversationId));
        redis.opsForList().rightPush(Objects.requireNonNull(key), Objects.requireNonNull(messageJson));
        redis.opsForList().trim(Objects.requireNonNull(key), -CONVERSATION_MESSAGES_MAX, -1);
    }

    /** 结束会话时删除会话消息列表 */
    public void deleteConversationMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return;
        String key = CsRedisKeys.conversationMessages(Objects.requireNonNull(conversationId));
        redis.delete(Objects.requireNonNull(key));
    }

    public void messageQueueRpush(String fromId, String json) {
        if (fromId == null || fromId.isBlank() || json == null) return;
        String key = CsRedisKeys.messageQueue(Objects.requireNonNull(fromId));
        redis.opsForList().rightPush(Objects.requireNonNull(key), Objects.requireNonNull(json));
        redis.opsForSet().add(CsRedisKeys.MESSAGE_QUEUE_FROMIDS, Objects.requireNonNull(fromId));
    }

    public void messageQueueSremFromIds(String fromId) {
        if (fromId == null || fromId.isBlank()) return;
        redis.opsForSet().remove(CsRedisKeys.MESSAGE_QUEUE_FROMIDS, Objects.requireNonNull(fromId));
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

    /** 结束会话：删除 user-conversation 成员、conversation-user、conversation-type、conversation-phone、conversation-messages（会话 id 即 fromId） */
    public void endSessionAtomic(String fromId, String userId, String conversationId) {
        if (fromId == null || userId == null || conversationId == null) return;
        String userConvKey = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        String convUserKey = CsRedisKeys.conversationUser(Objects.requireNonNull(conversationId));
        redis.opsForSet().remove(Objects.requireNonNull(userConvKey), Objects.requireNonNull(conversationId));
        redis.delete(Objects.requireNonNull(convUserKey));
        deleteConversationType(conversationId);
        deleteConversationPhone(conversationId);
        deleteConversationMessages(conversationId);
    }

    /** 发布消息到指定频道（用于多实例挤掉旧 WebSocket） */
    public void publish(String channel, String message) {
        if (channel == null || message == null) return;
        redis.convertAndSend(Objects.requireNonNull(channel), Objects.requireNonNull(message));
    }
}
