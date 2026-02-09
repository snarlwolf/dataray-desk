package com.skydawn.desk.dto;

public class GeneralMessageDto {

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /** 文本消息 */
        TEXT,
        /** 图片消息 */
        IMAGE,
        /** 贴纸消息（sticker） */
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
        /** 对某条消息的点赞/反应 */
        REACTION,
        /** 平台不支持的消息格式（events、poll 等） */
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
    
    private String messageSource;

    // ==================== 基础信息 ====================
    /** 业务账号 ID */
    private String accountId;
    /** 对外展示的号码（如公众号/渠道展示号） */
    private String officialPhoneNumber;
    /** 推广号/渠道账号 ID（会话路由与回复用） */
    private String officialAccount;

    // ==================== 消息信息 ====================
    /** 消息在来源侧的 ID */
    private String sourceMessageId;
    /** 消息类型 */
    private MessageType messageType;
    /** 消息状态 */
    private MessageStatus messageStatus;
    /** 时间戳（秒） */
    private long timestamp;

    // ==================== 发送者/接收者信息 ====================
    /** 会话 ID（推送时由后端填入，用于结束会话/转移） */
    private String conversationId;
    /** 客户/发送方 ID（如渠道侧用户手机号、内部用户ID、临时ID等） */
    private String clientId;
    /** 客户/发送方昵称 */
    private String clientName;

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

    /** 被引用消息 ID（回复某条消息或对某条消息点赞时指向的消息） */
    private String referencedMessageId;
    /** 反应 emoji（如 👍，仅 REACTION 类型时有值） */
    private String reactionEmoji;

    /** 纬度（位置消息） */
    private Double latitude;
    /** 经度（位置消息） */
    private Double longitude;

    /** 是否为我方（客服）发送；推送系统自动回复时设为 true */
    private Boolean isStaff;
    /** 是否为系统自动回复（如“暂不支持您所发送的消息格式！”）；用于更深蓝气泡样式 */
    private Boolean isSystemReply;
    /** 客服发送时的发送者名称，写入 Redis 会话消息时记录，便于审计与展示 */
    private String csStaffName;
    /** 系统自动回复时使用的文案（仅当 messageStatus=UNSUPPORTED 且需与默认「暂不支持」不同的保底文案时设置，如配置缺失时的「非常抱歉，由于未知原因…」） */
    private String unsupportedAutoReplyText;

    // ==================== Getters and Setters ====================
    
    

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getOfficialPhoneNumber() {
        return officialPhoneNumber;
    }

    public void setOfficialPhoneNumber(String officialPhoneNumber) {
        this.officialPhoneNumber = officialPhoneNumber;
    }

    public String getOfficialAccount() {
        return officialAccount;
    }

    public void setOfficialAccount(String officialAccount) {
        this.officialAccount = officialAccount;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(String sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
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

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
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

    public String getReferencedMessageId() {
        return referencedMessageId;
    }

    public void setReferencedMessageId(String referencedMessageId) {
        this.referencedMessageId = referencedMessageId;
    }

    public String getReactionEmoji() {
        return reactionEmoji;
    }

    public void setReactionEmoji(String reactionEmoji) {
        this.reactionEmoji = reactionEmoji;
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

    public Boolean getIsStaff() {
        return isStaff;
    }

    public void setIsStaff(Boolean isStaff) {
        this.isStaff = isStaff;
    }

    public Boolean getIsSystemReply() {
        return isSystemReply;
    }

    public void setIsSystemReply(Boolean isSystemReply) {
        this.isSystemReply = isSystemReply;
    }

    public String getCsStaffName() {
        return csStaffName;
    }

    public void setCsStaffName(String csStaffName) {
        this.csStaffName = csStaffName;
    }

    public String getUnsupportedAutoReplyText() {
        return unsupportedAutoReplyText;
    }

    public void setUnsupportedAutoReplyText(String unsupportedAutoReplyText) {
        this.unsupportedAutoReplyText = unsupportedAutoReplyText;
    }

    public void setMessageSource(String messageSource) {
		this.messageSource = messageSource;
	}
    
    public String getMessageSource() {
		return messageSource;
	}

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GeneralMessageDto{");
        sb.append("sourceMessageId='").append(sourceMessageId).append('\'');
        sb.append(", messageType=").append(messageType);
        sb.append(", messageStatus=").append(messageStatus);
        sb.append(", timestamp=").append(timestamp);
        sb.append(", clientId='").append(clientId).append('\'');
        sb.append(", clientName='").append(clientName).append('\'');
        if (textBody != null) sb.append(", textBody='").append(textBody).append('\'');
        if (mediaId != null) {
            sb.append(", mediaId='").append(mediaId).append('\'');
            sb.append(", mediaUrl='").append(mediaUrl).append('\'');
            sb.append(", mediaMimeType='").append(mediaMimeType).append('\'');
            if (mediaCaption != null) sb.append(", mediaCaption='").append(mediaCaption).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
