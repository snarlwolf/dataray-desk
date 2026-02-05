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
    /** 会话 ID（推送时由后端填入，用于结束会话/转移） */
    private String conversationId;
    /** 发送者 ID（WABA手机号、内部用户ID、临时ID等） */
    private String fromId;
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

    /** 被反应的消息 ID（仅 REACTION 类型） */
    private String reactionMessageId;
    /** 反应 emoji（如 👍） */
    private String reactionEmoji;

    /** 被回复的消息 ID（对方回复某条消息时，context.id） */
    private String quotedMessageId;

    /** 纬度（位置消息） */
    private Double latitude;
    /** 经度（位置消息） */
    private Double longitude;

    /** 是否为我方（客服）发送；推送系统自动回复时设为 true */
    private Boolean isStaff;
    /** 是否为系统自动回复（如“暂不支持您所发送的消息格式！”）；用于更深蓝气泡样式 */
    private Boolean isSystemReply;
    /** 客服发送时的发送者名称（senderName），写入 Redis 会话消息时记录，便于审计与展示 */
    private String senderName;
    /** 系统自动回复时使用的文案（仅当 messageStatus=UNSUPPORTED 且需与默认「暂不支持」不同的保底文案时设置，如配置缺失时的「非常抱歉，由于未知原因…」） */
    private String unsupportedAutoReplyText;

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

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getFromId() {
        return fromId;
    }

    public void setFromId(String fromId) {
        this.fromId = fromId;
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

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
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
        sb.append("messageId='").append(messageId).append('\'');
        sb.append(", messageType=").append(messageType);
        sb.append(", messageStatus=").append(messageStatus);
        sb.append(", timestamp=").append(timestamp);
        sb.append(", fromId='").append(fromId).append('\'');
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
