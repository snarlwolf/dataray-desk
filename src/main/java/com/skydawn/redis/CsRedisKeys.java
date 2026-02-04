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
    public static final String MESSAGE_QUEUE = PREFIX + "message-queue:";
    public static final String MESSAGE_QUEUE_FROMIDS = PREFIX + "message-queue-fromids";
    public static final String LOCK_FROM = PREFIX + "lock:from:";
    public static final String LOCK_CONVERSATION = PREFIX + "lock:conversation:";
    public static final String LOCK_TRANSFER = PREFIX + "lock:transfer:";

    /** Redis Pub/Sub 频道：同账号在其他实例新建立 WebSocket 时，通知其他实例关闭该客服（登录名）的旧连接 */
    public static final String CHANNEL_WS_CLOSE_ELSEWHERE = PREFIX + "ws:close-elsewhere";

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
    public static String messageQueue(String fromId) { return MESSAGE_QUEUE + StringTools.escapeForRedisKeySegment(fromId); }
    public static String lockFrom(String fromId) { return LOCK_FROM + StringTools.escapeForRedisKeySegment(fromId); }
    public static String lockConversation(String conversationId) { return LOCK_CONVERSATION + StringTools.escapeForRedisKeySegment(conversationId); }
    public static String lockTransfer(String userId) { return LOCK_TRANSFER + StringTools.escapeForRedisKeySegment(userId); }

    private CsRedisKeys() {}
}
