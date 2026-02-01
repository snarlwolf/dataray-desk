package com.skydawn.common;

/**
 * Central definitions shared across modules.
 * Keep these enums consistent with DB CHECK constraints / defaults.
 */
public final class Defs {
    private Defs() {}

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
    
}