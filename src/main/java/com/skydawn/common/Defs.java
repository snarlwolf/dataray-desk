package com.skydawn.common;

/**
 * Central definitions shared across modules.
 * Keep these enums consistent with DB CHECK constraints / defaults.
 */
public final class Defs {
    private Defs() {}

    /** 本系统的代码，以此取数据库中适合本系统的配置信息 */
    public static final String SYS_CODE = "skydawn_desk";
    public static final String ALL = "all";

    /** 下游鉴权 Header（建议固定用 Header，不要拼 URL） */
    public static final String HTTP_HEAD_TOKEN_NAME = "X-Skydawn-Token";

    public static final String HTTP_HEAD_DATA_SOURCE_NAME = "data-source";

    // ==================== API 签名验证 Headers ====================
    /** 应用ID */
    public static final String HTTP_HEAD_APP_ID = "X-Skydawn-AppId";
    /** 请求时间戳（毫秒） */
    public static final String HTTP_HEAD_TIMESTAMP = "X-Skydawn-Timestamp";
    /** 随机字符串（防重放） */
    public static final String HTTP_HEAD_NONCE = "X-Skydawn-Nonce";
    /** HMAC-SHA256 签名 */
    public static final String HTTP_HEAD_SIGNATURE = "X-Skydawn-Signature";

    /** 全局属性配置 Key 常量 */
    public static final String PROP_KEY_MESSAGE_ENABLE_PRE = "message.enable.";
    /** 客服最大同时会话数配置 key，默认 20 */
    public static final String PROP_KEY_DESK_CONVERSATION_MAX_COUNT = "desk.conversation.max-count";
    /** 客服离线多少秒后自动转移其会话，未配置时默认 500 秒 */
    public static final String PROP_KEY_DESK_OFFLINE_KEEP_CONVERSATION = "desk.unline.keep-conversation";

}