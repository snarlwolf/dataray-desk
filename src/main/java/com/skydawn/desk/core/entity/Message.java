package com.skydawn.desk.core.entity;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 消息表 message（新表结构）
 */
@Data
public class Message {

    private Long id;
    private Long conversationId;
    private String messageSource;
    private String officialPhoneNumber;
    private String officialAccount;
    private String sourceMessageId;
    private String messageType;
    private String messageStatus;
    private OffsetDateTime sendTime;
    private String redisConversationId;
    /** 客户标识，来自外部系统，存为字符串以兼容各端类型 */
    private String clientId;
    private String clientName;
    private String messageLanguage;
    private String textBody;
    private String mediaId;
    private String mediaUrl;
    private String mediaMimeType;
    private String mediaSha256;
    private String mediaCaption;
    private String referencedMessageId;
    private String reactionEmoji;
    private Double latitude;
    private Double longitude;
    private Integer isStaff;
    private String sysUserId;
    private String delFlag;
    private String remarks;
    private OffsetDateTime createTime;
    private String createBy;
    private OffsetDateTime updateTime;
    private String updateBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getMessageSource() { return messageSource; }
    public void setMessageSource(String messageSource) { this.messageSource = messageSource; }
    public String getOfficialPhoneNumber() { return officialPhoneNumber; }
    public void setOfficialPhoneNumber(String officialPhoneNumber) { this.officialPhoneNumber = officialPhoneNumber; }
    public String getOfficialAccount() { return officialAccount; }
    public void setOfficialAccount(String officialAccount) { this.officialAccount = officialAccount; }
    public String getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getMessageStatus() { return messageStatus; }
    public void setMessageStatus(String messageStatus) { this.messageStatus = messageStatus; }
    public OffsetDateTime getSendTime() { return sendTime; }
    public void setSendTime(OffsetDateTime sendTime) { this.sendTime = sendTime; }
    public String getRedisConversationId() { return redisConversationId; }
    public void setRedisConversationId(String redisConversationId) { this.redisConversationId = redisConversationId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getMessageLanguage() { return messageLanguage; }
    public void setMessageLanguage(String messageLanguage) { this.messageLanguage = messageLanguage; }
    public String getTextBody() { return textBody; }
    public void setTextBody(String textBody) { this.textBody = textBody; }
    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMediaMimeType() { return mediaMimeType; }
    public void setMediaMimeType(String mediaMimeType) { this.mediaMimeType = mediaMimeType; }
    public String getMediaSha256() { return mediaSha256; }
    public void setMediaSha256(String mediaSha256) { this.mediaSha256 = mediaSha256; }
    public String getMediaCaption() { return mediaCaption; }
    public void setMediaCaption(String mediaCaption) { this.mediaCaption = mediaCaption; }
    public String getReferencedMessageId() { return referencedMessageId; }
    public void setReferencedMessageId(String referencedMessageId) { this.referencedMessageId = referencedMessageId; }
    public String getReactionEmoji() { return reactionEmoji; }
    public void setReactionEmoji(String reactionEmoji) { this.reactionEmoji = reactionEmoji; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Integer getIsStaff() { return isStaff; }
    public void setIsStaff(Integer isStaff) { this.isStaff = isStaff; }
    public String getSysUserId() { return sysUserId; }
    public void setSysUserId(String sysUserId) { this.sysUserId = sysUserId; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public OffsetDateTime getCreateTime() { return createTime; }
    public void setCreateTime(OffsetDateTime createTime) { this.createTime = createTime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public OffsetDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(OffsetDateTime updateTime) { this.updateTime = updateTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
}
