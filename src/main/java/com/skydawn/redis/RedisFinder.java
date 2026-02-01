package com.skydawn.redis;

import com.skydawn.common.utils.StringTools;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

/**
 * 客服台 Redis 只读查询（desk:cs）
 */
public class RedisFinder {

    private final StringRedisTemplate redis;

    public RedisFinder(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isUserOnline(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        String key = CsRedisKeys.users(Objects.requireNonNull(userId));
        return Boolean.TRUE.equals(redis.hasKey(Objects.requireNonNull(key)));
    }

    public String getLoginTime(String userId) {
        if (userId == null || userId.isBlank()) return null;
        String key = CsRedisKeys.users(Objects.requireNonNull(userId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /** 若 fromId 有进行中会话则返回会话 id（即 fromId），否则 null。用 conversationtype 键存在表示“有会话”。 */
    public String getFromConversation(String fromId) {
        if (fromId == null || fromId.isBlank()) return null;
        String key = CsRedisKeys.conversationType(Objects.requireNonNull(fromId));
        return Boolean.TRUE.equals(redis.hasKey(Objects.requireNonNull(key))) ? fromId : null;
    }

    public String getConversationUser(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        String key = CsRedisKeys.conversationUser(Objects.requireNonNull(conversationId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /** 会话 id 即 fromId，直接返回 conversationId，不查 Redis。 */
    public String getConversationFromId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        return conversationId;
    }

    /** 会话渠道类型，如 "waba" */
    public String getConversationType(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        String key = CsRedisKeys.conversationType(Objects.requireNonNull(conversationId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /** WABA 会话的 phoneNumberId（回复时用） */
    public String getConversationPhone(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        String key = CsRedisKeys.conversationPhone(Objects.requireNonNull(conversationId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /** 会话消息列表（每条为 JSON，与推送格式一致），按时间顺序 */
    public List<String> getConversationMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return List.of();
        String key = CsRedisKeys.conversationMessages(Objects.requireNonNull(conversationId));
        List<String> list = redis.opsForList().range(Objects.requireNonNull(key), 0, -1);
        return list != null ? list : List.of();
    }

    public Set<String> getUserConversationList(String userId) {
        if (userId == null || userId.isBlank()) return Set.of();
        String key = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        Set<String> members = redis.opsForSet().members(Objects.requireNonNull(key));
        return members != null ? members : Set.of();
    }

    public int getUserConversationCount(String userId) {
        if (userId == null || userId.isBlank()) return 0;
        String key = CsRedisKeys.userConversation(Objects.requireNonNull(userId));
        Long size = redis.opsForSet().size(Objects.requireNonNull(key));
        return size != null ? size.intValue() : 0;
    }

    public String getUserOfflineSince(String userId) {
        if (userId == null || userId.isBlank()) return null;
        String key = CsRedisKeys.userOfflineSince(Objects.requireNonNull(userId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /** 所有在线客服登录名（desk:cs:users:*，key 片段经转义） */
    public List<String> getAllOnlineUserIds() {
        Set<String> keys = redis.keys(CsRedisKeys.USERS + "*");
        if (keys == null || keys.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        for (String key : keys) {
            if (key.startsWith(CsRedisKeys.USERS)) {
                String segment = key.substring(CsRedisKeys.USERS.length());
                ids.add(StringTools.unescapeForRedisKeySegment(segment));
            }
        }
        return ids;
    }

    /**
     * 用于分配规则：在线用户及其会话数、登录时间。
     * 返回 List of [userId, conversationCount, logintime]，按会话数升序、登录时间降序排序。
     */
    public List<OnlineUserSlot> getOnlineUsersWithConversationCountAndLoginTime() {
        List<String> userIds = getAllOnlineUserIds();
        List<OnlineUserSlot> list = new ArrayList<>();
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            String safeUid = Objects.requireNonNull(uid);
            int count = getUserConversationCount(safeUid);
            String logintime = getLoginTime(safeUid);
            list.add(new OnlineUserSlot(safeUid, count, logintime != null ? logintime : ""));
        }
        list.sort(Comparator
                .comparingInt(OnlineUserSlot::conversationCount)
                .thenComparing(OnlineUserSlot::loginTime, Comparator.reverseOrder()));
        return list;
    }

    public record OnlineUserSlot(String userId, int conversationCount, String loginTime) {}

    public long messageQueueLength(String fromId) {
        if (fromId == null || fromId.isBlank()) return 0;
        String key = CsRedisKeys.messageQueue(Objects.requireNonNull(fromId));
        Long len = redis.opsForList().size(Objects.requireNonNull(key));
        return len != null ? len : 0;
    }

    public String messageQueueLpop(String fromId) {
        if (fromId == null || fromId.isBlank()) return null;
        String key = CsRedisKeys.messageQueue(Objects.requireNonNull(fromId));
        return redis.opsForList().leftPop(Objects.requireNonNull(key));
    }

    /** 有待处理消息的 fromId 列表（keys 或 SET） */
    public Set<String> getMessageQueueFromIds() {
        Set<String> fromIds = redis.opsForSet().members(CsRedisKeys.MESSAGE_QUEUE_FROMIDS);
        if (fromIds != null && !fromIds.isEmpty()) return fromIds;
        Set<String> keys = redis.keys(CsRedisKeys.MESSAGE_QUEUE + "*");
        if (keys == null || keys.isEmpty()) return Set.of();
        Set<String> ids = new HashSet<>();
        for (String key : keys) {
            if (key.startsWith(CsRedisKeys.MESSAGE_QUEUE)) {
                String segment = key.substring(CsRedisKeys.MESSAGE_QUEUE.length());
                ids.add(StringTools.unescapeForRedisKeySegment(segment));
            }
        }
        return ids;
    }

    /** 有 user-conversation 且不在 users 中的客服登录名（用于自动转移、清理） */
    public List<String> getOfflineUserIdsWithConversations() {
        Set<String> keys = redis.keys(CsRedisKeys.USER_CONVERSATION + "*");
        if (keys == null || keys.isEmpty()) return List.of();
        List<String> offline = new ArrayList<>();
        for (String key : keys) {
            if (!key.startsWith(CsRedisKeys.USER_CONVERSATION)) continue;
            String segment = key.substring(CsRedisKeys.USER_CONVERSATION.length());
            String loginName = StringTools.unescapeForRedisKeySegment(segment);
            if (!isUserOnline(loginName)) offline.add(loginName);
        }
        return offline;
    }

    /** conversation-user 存在但 conversationtype 不存在的会话 id 列表（孤儿，会话 id 即 fromId） */
    public List<String> findOrphanConversationIds() {
        Set<String> keys = redis.keys(CsRedisKeys.CONVERSATION_USER + "*");
        if (keys == null || keys.isEmpty()) return List.of();
        List<String> orphans = new ArrayList<>();
        for (String key : keys) {
            if (!key.startsWith(CsRedisKeys.CONVERSATION_USER)) continue;
            String segment = key.substring(CsRedisKeys.CONVERSATION_USER.length());
            String convId = StringTools.unescapeForRedisKeySegment(segment);
            if (convId == null || convId.isEmpty()) continue;
            String keyType = CsRedisKeys.conversationType(convId);
            if (!Boolean.TRUE.equals(redis.hasKey(Objects.requireNonNull(keyType)))) {
                orphans.add(convId);
            }
        }
        return orphans;
    }
}
