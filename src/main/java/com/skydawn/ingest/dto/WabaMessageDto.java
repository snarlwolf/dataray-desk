package com.skydawn.ingest.dto;

/**
 * WhatsApp Business API 消息 DTO
 * 扁平化结构，便于业务处理
 */
public class WabaMessageDto {

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /** 文本消息 */
        TEXT,
        /** 图片消息 */
        IMAGE,
        /** 贴纸消息（sticker，WebP 等） */
        STICKER,
        /** 视频消息 */
        VIDEO,
        /** 音频消息 */
        AUDIO,
        /** 文档消息 */
        DOCUMENT,
        /** 位置消息 */
        LOCATION,
        /** 联系人消息 */
        CONTACTS,
        /** 状态更新（sent/delivered/read） */
        STATUS,
        /** 对某条消息的点赞/反应（reaction） */
        REACTION,
        /** 平台不支持的消息格式（events、poll 等，type=unsupported） */
        UNSUPPORTED,
        /** 未知类型 */
        UNKNOWN
    }

    /**
     * 消息状态枚举
     */
    public enum MessageStatus {
        /** 正常消息 */
        NORMAL,
        /** 已发送 */
        SENT,
        /** 已送达 */
        DELIVERED,
        /** 已读 */
        READ,
        /** 暂不支持的消息格式（用户期望回复，可建会话、可取昵称） */
        UNSUPPORTED,
        /** 未知状态 */
        UNKNOWN
    }

    // ==================== 基础信息 ====================
    /** WhatsApp Business Account ID */
    private String accountId;
    /** 显示的电话号码 */
    private String displayPhoneNumber;
    /** 电话号码 ID */
    private String phoneNumberId;

    // ==================== 消息信息 ====================
    /** 消息 ID */
    private String messageId;
    /** 消息类型 */
    private MessageType messageType;
    /** 消息状态 */
    private MessageStatus messageStatus;
    /** 时间戳（秒） */
    private long timestamp;

    // ==================== 发送者/接收者信息 ====================
    /** 发送者 WhatsApp ID（手机号） */
    private String fromWaId;
    /** 发送者昵称 */
    private String fromProfileName;
    /** 接收者 ID（状态消息时使用） */
    private String recipientId;

    // ==================== 文本内容 ====================
    /** 文本内容 */
    private String textBody;

    // ==================== 媒体内容 ====================
    /** 媒体 ID */
    private String mediaId;
    /** 媒体 URL */
    private String mediaUrl;
    /** 媒体 MIME 类型 */
    private String mediaMimeType;
    /** 媒体 SHA256 */
    private String mediaSha256;
    /** 媒体标题/说明（图片+文字时的文字） */
    private String mediaCaption;

    // ==================== 反应消息相关（type=reaction） ====================
    /** 被反应的消息 ID（reaction.message_id） */
    private String reactionMessageId;
    /** 反应 emoji（reaction.emoji，如 👍） */
    private String reactionEmoji;

    // ==================== 回复/引用消息（context） ====================
    /** 被回复的消息 ID（context.id），对方回复某条消息时存在 */
    private String quotedMessageId;

    // ==================== 位置消息（type=location） ====================
    /** 纬度（location.latitude） */
    private Double latitude;
    /** 经度（location.longitude） */
    private Double longitude;

    // ==================== 状态消息相关 ====================
    /** 计费信息 - 是否计费 */
    private Boolean billable;
    /** 计费信息 - 定价模型 */
    private String pricingModel;
    /** 计费信息 - 类别 */
    private String pricingCategory;
    /** 计费信息 - 类型 */
    private String pricingType;

    // ==================== 原始数据 ====================
    /** 原始 JSON（用于调试或存储） */
    private String rawJson;

    // ==================== Getters and Setters ====================

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getDisplayPhoneNumber() {
        return displayPhoneNumber;
    }

    public void setDisplayPhoneNumber(String displayPhoneNumber) {
        this.displayPhoneNumber = displayPhoneNumber;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public MessageStatus getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(MessageStatus messageStatus) {
        this.messageStatus = messageStatus;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getFromWaId() {
        return fromWaId;
    }

    public void setFromWaId(String fromWaId) {
        this.fromWaId = fromWaId;
    }

    public String getFromProfileName() {
        return fromProfileName;
    }

    public void setFromProfileName(String fromProfileName) {
        this.fromProfileName = fromProfileName;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getMediaMimeType() {
        return mediaMimeType;
    }

    public void setMediaMimeType(String mediaMimeType) {
        this.mediaMimeType = mediaMimeType;
    }

    public String getMediaSha256() {
        return mediaSha256;
    }

    public void setMediaSha256(String mediaSha256) {
        this.mediaSha256 = mediaSha256;
    }

    public String getMediaCaption() {
        return mediaCaption;
    }

    public void setMediaCaption(String mediaCaption) {
        this.mediaCaption = mediaCaption;
    }

    public String getReactionMessageId() {
        return reactionMessageId;
    }

    public void setReactionMessageId(String reactionMessageId) {
        this.reactionMessageId = reactionMessageId;
    }

    public String getReactionEmoji() {
        return reactionEmoji;
    }

    public void setReactionEmoji(String reactionEmoji) {
        this.reactionEmoji = reactionEmoji;
    }

    public String getQuotedMessageId() {
        return quotedMessageId;
    }

    public void setQuotedMessageId(String quotedMessageId) {
        this.quotedMessageId = quotedMessageId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Boolean getBillable() {
        return billable;
    }

    public void setBillable(Boolean billable) {
        this.billable = billable;
    }

    public String getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(String pricingModel) {
        this.pricingModel = pricingModel;
    }

    public String getPricingCategory() {
        return pricingCategory;
    }

    public void setPricingCategory(String pricingCategory) {
        this.pricingCategory = pricingCategory;
    }

    public String getPricingType() {
        return pricingType;
    }

    public void setPricingType(String pricingType) {
        this.pricingType = pricingType;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WabaMessageDto{");
        sb.append("messageId='").append(messageId).append('\'');
        sb.append(", messageType=").append(messageType);
        sb.append(", messageStatus=").append(messageStatus);
        sb.append(", timestamp=").append(timestamp);
        sb.append(", fromWaId='").append(fromWaId).append('\'');
        sb.append(", fromProfileName='").append(fromProfileName).append('\'');
        
        // 文本内容
        if (textBody != null) {
            sb.append(", textBody='").append(textBody).append('\'');
        }
        
        // 媒体内容
        if (mediaId != null) {
            sb.append(", mediaId='").append(mediaId).append('\'');
            sb.append(", mediaUrl='").append(mediaUrl).append('\'');
            sb.append(", mediaMimeType='").append(mediaMimeType).append('\'');
            if (mediaCaption != null) {
                sb.append(", mediaCaption='").append(mediaCaption).append('\'');
            }
        }
        
        // 状态消息相关
        if (recipientId != null) {
            sb.append(", recipientId='").append(recipientId).append('\'');
        }
        
        sb.append('}');
        return sb.toString();
    }
}
