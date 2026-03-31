package com.skydawn.redis;

import com.skydawn.common.utils.StringTools;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
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

    /**
     * 若该（推广号, 用户）有进行中会话则返回会话 id（格式 phoneNumberId-fromId 或仅 fromId），否则 null。
     * phoneNumberId 为空时按仅 fromId 的旧格式查找。
     */
    public String getFromConversation(String phoneNumberId, String fromId) {
        if (fromId == null || fromId.isBlank()) return null;
        String conversationId = CsRedisKeys.formConversationId(phoneNumberId, fromId);
        String keyType = CsRedisKeys.conversationType(Objects.requireNonNull(conversationId));
        return Boolean.TRUE.equals(redis.hasKey(Objects.requireNonNull(keyType))) ? conversationId : null;
    }

    public String getConversationUser(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        String key = CsRedisKeys.conversationUser(Objects.requireNonNull(conversationId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /** 从会话 id 解析出客户 fromId（用户手机号）。格式为 phoneNumberId-fromId 时取分隔符后部分，否则原样返回。 */
    public String getConversationFromId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        return CsRedisKeys.parseFromIdFromConversationId(conversationId);
    }

    /** 会话渠道类型，如 "waba" */
    public String getConversationType(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        String key = CsRedisKeys.conversationType(Objects.requireNonNull(conversationId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /**
     * 从 AI 系统写入的 Redis key（desk:cs:ai:conversation-db-id:）中读取 conversation 表主键。
     * 用于会话结束时定位 AI 系统创建的 conversation 记录和 transfer-agent-key。
     */
    public Long getAiConversationDbId(String redisConversationId) {
        if (redisConversationId == null || redisConversationId.isBlank()) return null;
        String key = CsRedisKeys.aiConversationDbId(redisConversationId);
        String val = redis.opsForValue().get(key);
        if (val == null || val.isBlank()) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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

    /** 从负载 ZSET 取该客服当前负载（会话数），不在 ZSET 或未设置时视为 0 */
    public int getLoadScore(String userId) {
        if (userId == null || userId.isBlank()) return 0;
        Double score = redis.opsForZSet().score(CsRedisKeys.LOAD_ZSET, userId);
        return score != null ? score.intValue() : 0;
    }

    /** 读取指定用户的在线档案 JSON，不存在时返回 null */
    public String getUserProfile(String userName) {
        if (userName == null || userName.isBlank()) return null;
        return redis.opsForValue().get(CsRedisKeys.userProfile(userName));
    }

    /**
     * 返回所有在线用户（desk:cs:users:*）中，拥有 user-profile 的用户名列表。
     * 用于构建同部门在线同事列表时批量读取档案。
     */
    public List<String> getOnlineUserNamesWithProfile() {
        List<String> onlineUsers = getAllOnlineUserIds();
        if (onlineUsers.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String userName : onlineUsers) {
            if (getUserProfile(userName) != null) {
                result.add(userName);
            }
        }
        return result;
    }

    /** 分配时排除的用户（AI 等非人工客服，不参与会话分配） */
    private static final Set<String> EXCLUDED_FROM_ASSIGNMENT = Set.of("AI", "AISYSTEM", "TRANSFERING");

    /**
     * 分配用候选客服：在线且负载 &lt; maxCount，按负载升序、登录时间升序（先登录优先）。
     * 排除 AI 等非人工客服，不参与会话分配。
     */
    public List<String> getOrderedCandidateUserIdsForAssignment(int maxCount) {
        List<String> online = getAllOnlineUserIds();
        if (online.isEmpty()) return List.of();
        List<LoadAndLogin> list = new ArrayList<>();
        for (String uid : online) {
            if (EXCLUDED_FROM_ASSIGNMENT.contains(uid)) continue;
            int load = getLoadScore(uid);
            if (load >= maxCount) continue;
            String loginTime = getLoginTime(uid);
            list.add(new LoadAndLogin(uid, load, loginTime != null ? loginTime : ""));
        }
        list.sort(Comparator.comparingInt(LoadAndLogin::load).thenComparing(LoadAndLogin::loginTime));
        return list.stream().map(LoadAndLogin::userId).collect(java.util.stream.Collectors.toList());
    }

    private record LoadAndLogin(String userId, int load, String loginTime) {}

    public String getUserOfflineSince(String userId) {
        if (userId == null || userId.isBlank()) return null;
        String key = CsRedisKeys.userOfflineSince(Objects.requireNonNull(userId));
        return redis.opsForValue().get(Objects.requireNonNull(key));
    }

    /** 所有在线客服登录名（desk:cs:users:*，key 片段经转义），用 SCAN 避免阻塞 Redis */
    public List<String> getAllOnlineUserIds() {
        List<String> keys = scanKeys(CsRedisKeys.USERS + "*");
        List<String> ids = new ArrayList<>(keys.size());
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

    /**
     * 返回 conversation-user 中 value 等于 userId 的会话 id 集合。
     * 用于登录时以 user-conversation 为准重建 conversation-user，删除多余碎片。
     */
    public Set<String> getConversationIdsOwnedByUser(String userId) {
        if (userId == null || userId.isBlank()) return Set.of();
        List<String> keys = scanKeys(CsRedisKeys.CONVERSATION_USER + "*");
        Set<String> result = new HashSet<>();
        for (String key : keys) {
            if (!key.startsWith(CsRedisKeys.CONVERSATION_USER)) continue;
            String val = redis.opsForValue().get(key);
            if (!userId.equals(val)) continue;
            String segment = key.substring(CsRedisKeys.CONVERSATION_USER.length());
            String convId = StringTools.unescapeForRedisKeySegment(segment);
            if (convId != null && !convId.isEmpty()) result.add(convId);
        }
        return result;
    }

    /** 有 user-conversation 且不在 users 中的客服登录名（用于自动转移、清理） */
    public List<String> getOfflineUserIdsWithConversations() {
        List<String> keys = scanKeys(CsRedisKeys.USER_CONVERSATION + "*");
        if (keys.isEmpty()) return List.of();
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
        List<String> keys = scanKeys(CsRedisKeys.CONVERSATION_USER + "*");
        if (keys.isEmpty()) return List.of();
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

    /**
     * 返回所有有 user-profile key 的用户名（不区分是否在线），用于检测僵尸档案。
     * key 格式：{@link CsRedisKeys#USER_PROFILE}{escapedUserName}
     */
    public List<String> getAllProfileUserNames() {
        List<String> keys = scanKeys(CsRedisKeys.USER_PROFILE + "*");
        List<String> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            if (key.startsWith(CsRedisKeys.USER_PROFILE)) {
                String segment = key.substring(CsRedisKeys.USER_PROFILE.length());
                String userName = StringTools.unescapeForRedisKeySegment(segment);
                if (userName != null && !userName.isEmpty()) result.add(userName);
            }
        }
        return result;
    }

    /**
     * 用 SCAN 替代 KEYS，以增量方式扫描匹配的 key，避免阻塞 Redis。
     * KEYS 是 O(N) 全量扫描，会在扫描期间挂起整个 Redis 实例。
     */
    private List<String> scanKeys(String pattern) {
        List<String> result = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        redis.execute((RedisCallback<Void>) conn -> {
            Cursor<byte[]> cursor = conn.keyCommands().scan(options);
            try {
                cursor.forEachRemaining(k -> result.add(new String(k, StandardCharsets.UTF_8)));
            } finally {
                try { cursor.close(); } catch (Exception ignored) {}
            }
            return null;
        });
        return result;
    }
}
