package com.skydawn.redis;

import com.skydawn.common.utils.StringTools;

/**
 * 客服台 Redis key 常量（desk:cs:）
 * 会话相关 key 的 fromId/conversationId、客服 user 标识（登录名）会经 {@link StringTools#escapeForRedisKeySegment} 转义。
 */
public final class CsRedisKeys {

    private static final String PREFIX = "desk:cs:";

    public static final String USERS = PREFIX + "users:";
    public static final String USER_OFFLINE_SINCE = PREFIX + "user-offline-since:";
    public static final String USER_CONVERSATION = PREFIX + "user-conversation:";
    public static final String CONVERSATION_USER = PREFIX + "conversation-user:";
    /** 会话渠道类型，如 "waba"（消息进来时写入，用于回复时选择发送方式） */
    public static final String CONVERSATION_TYPE = PREFIX + "conversationtype:";
    /** WABA 会话的 phoneNumberId（消息进来时写入，回复时用） */
    public static final String CONVERSATION_PHONE = PREFIX + "conversation-phone:";
    /** 会话消息列表（LIST，每条为 JSON；结束会话时删除） */
    public static final String CONVERSATION_MESSAGES = PREFIX + "conversation-messages:";
    /** Redis 会话 id -> 库表 conversation.id（仅缓存，不入 conversation 表，供消息入库时查 conversation_id） */
    public static final String CONVERSATION_DB_ID = PREFIX + "conversation-db-id:";
    /**
     * 用户在线档案（JSON：deptId/nickName/avatar），WebSocket 连接时写入，断开时删除。
     * 供同部门在线同事列表构建使用，不需要实时查数据库。
     */
    public static final String USER_PROFILE = PREFIX + "user-profile:";

    public static final String LOCK_FROM = PREFIX + "lock:from:";
    public static final String LOCK_CONVERSATION = PREFIX + "lock:conversation:";
    public static final String LOCK_TRANSFER = PREFIX + "lock:transfer:";

    /** 客服负载 ZSET：member=userId(登录名)，score=当前会话数；用于 ZRANGE 0 0 取负载最小 */
    public static final String LOAD_ZSET = PREFIX + "load:zset";

    /** Redis Pub/Sub 频道：同账号在其他实例新建立 WebSocket 时，通知其他实例关闭该客服（登录名）的旧连接 */
    public static final String CHANNEL_WS_CLOSE_ELSEWHERE = PREFIX + "ws:close-elsewhere";

    /**
     * Redis Pub/Sub 频道：客服上线/下线时广播所属 deptId，各实例收到后向本地同部门在线用户推送全量同事列表。
     * 消息内容：deptId 数字字符串，无部门时为 "null"。
     */
    public static final String CHANNEL_COLLEAGUES_NOTIFY = PREFIX + "colleagues:notify";

    /** 会话 id 中 phoneNumberId 与 fromId 的分隔符，格式为 phoneNumberId + SEP + fromId，支持同一用户联系不同推广号 */
    public static final String CONVERSATION_ID_SEPARATOR = "-";

    /**
     * 组成会话 id：推广号手机 id-用户手机号。同一用户联系不同推广号时会话隔离。
     * 若 phoneNumberId 为空则退回仅用 fromId（兼容非 WABA 或旧数据）。
     */
    public static String formConversationId(String phoneNumberId, String fromId) {
        if (fromId == null || fromId.isBlank()) return null;
        if (phoneNumberId == null || phoneNumberId.isBlank()) return fromId;
        return phoneNumberId + CONVERSATION_ID_SEPARATOR + fromId;
    }

    /**
     * 从会话 id 解析出客户 fromId（用户手机号）。格式为 phoneNumberId-fromId 时取分隔符后部分。
     */
    public static String parseFromIdFromConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        int i = conversationId.indexOf(CONVERSATION_ID_SEPARATOR);
        return i >= 0 ? conversationId.substring(i + 1) : conversationId;
    }

    /** userId 为客服登录名（userName），会转义后拼入 key */
    public static String users(String userId) { return USERS + StringTools.escapeForRedisKeySegment(userId); }
    public static String userOfflineSince(String userId) { return USER_OFFLINE_SINCE + StringTools.escapeForRedisKeySegment(userId); }
    public static String userConversation(String userId) { return USER_CONVERSATION + StringTools.escapeForRedisKeySegment(userId); }
    public static String conversationUser(String conversationId) { return CONVERSATION_USER + StringTools.escapeForRedisKeySegment(conversationId); }
    public static String conversationType(String conversationId) { return CONVERSATION_TYPE + StringTools.escapeForRedisKeySegment(conversationId); }
    public static String conversationPhone(String conversationId) { return CONVERSATION_PHONE + StringTools.escapeForRedisKeySegment(conversationId); }
    public static String conversationMessages(String conversationId) { return CONVERSATION_MESSAGES + StringTools.escapeForRedisKeySegment(conversationId); }
    /** Redis 会话 id 对应库表 conversation.id 的缓存 key */
    public static String conversationDbId(String redisConversationId) { return CONVERSATION_DB_ID + StringTools.escapeForRedisKeySegment(redisConversationId); }
    public static String userProfile(String userName) { return USER_PROFILE + StringTools.escapeForRedisKeySegment(userName); }
    public static String lockFrom(String fromId) { return LOCK_FROM + StringTools.escapeForRedisKeySegment(fromId); }
    public static String lockConversation(String conversationId) { return LOCK_CONVERSATION + StringTools.escapeForRedisKeySegment(conversationId); }
    public static String lockTransfer(String userId) { return LOCK_TRANSFER + StringTools.escapeForRedisKeySegment(userId); }

    // ---------- 外部系统 key（desk:cs:ai:），只读/清理，不由本系统写入 ----------
    /** AI 系统写入的 Redis会话ID -> conversation表主键 映射（只读，供本系统查询正确的 DB ID） */
    public static final String AI_CONVERSATION_DB_ID = "desk:cs:ai:conversation-db-id:";

    /** AI 系统会话转人工标记 key（由 AI 系统创建，会话结束时本系统负责清理） */
    public static final String AI_CONVERSATION_TRANSFER_AGENT_KEY = "desk:cs:ai:conversation-transfer-agent-key:";

    /** 返回 AI 系统 conversation-db-id 的完整 key */
    public static String aiConversationDbId(String redisConversationId) {
        return AI_CONVERSATION_DB_ID + StringTools.escapeForRedisKeySegment(redisConversationId);
    }

    /** 返回 AI 系统会话转人工标记的完整 key */
    public static String aiConversationTransferAgentKey(String conversationId) {
        return AI_CONVERSATION_TRANSFER_AGENT_KEY + StringTools.escapeForRedisKeySegment(conversationId);
    }

    // ---------- 待分配会话队列（desk:uq:），FIFO + 去重 ----------
    private static final String UQ_PREFIX = "desk:uq:";
    /** 待分配会话 LIST，FIFO */
    public static final String PENDING_CONVERSATIONS_LIST = UQ_PREFIX + "pending-conversations:list";
    /** 待分配会话 SET，用于去重 */
    public static final String PENDING_CONVERSATIONS_SET = UQ_PREFIX + "pending-conversations:set";

    private CsRedisKeys() {}
}
