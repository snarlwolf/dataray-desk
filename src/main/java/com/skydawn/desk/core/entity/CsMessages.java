package com.skydawn.desk.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("cs_messages")
public class CsMessages {

    @TableId(type = IdType.INPUT)
    private UUID id;                     // UUID 主键（手动设置）

    private String conversationId;
    private String senderType;
    private Long senderId;
    private String senderName;

    private String messageContent;
    private OffsetDateTime sendTime;

    private String sendStatus;
    private String messageLanguage;

    private Boolean isInternal;
    private String messageShowStatus;
    
    private String messageType;  // 消息类型，如 "whatsapp", "telegram" 等
    
    private String mediaType;     // 媒体类型，如 "image", "video", "audio" 等
    private String mediaId;       // WhatsApp media_id
    private String mediaUrl;      // 媒体文件 URL
    private String caption;       // 图片/视频的说明文字

    private OffsetDateTime createdAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(String conversationId) {
		this.conversationId = conversationId;
	}

	public String getSenderType() {
		return senderType;
	}

	public void setSenderType(String senderType) {
		this.senderType = senderType;
	}

	public Long getSenderId() {
		return senderId;
	}

	public void setSenderId(Long senderId) {
		this.senderId = senderId;
	}

	public String getSenderName() {
		return senderName;
	}

	public void setSenderName(String senderName) {
		this.senderName = senderName;
	}

	public String getMessageContent() {
		return messageContent;
	}

	public void setMessageContent(String messageContent) {
		this.messageContent = messageContent;
	}

	public OffsetDateTime getSendTime() {
		return sendTime;
	}

	public void setSendTime(OffsetDateTime sendTime) {
		this.sendTime = sendTime;
	}

	public String getSendStatus() {
		return sendStatus;
	}

	public void setSendStatus(String sendStatus) {
		this.sendStatus = sendStatus;
	}

	public String getMessageLanguage() {
		return messageLanguage;
	}

	public void setMessageLanguage(String messageLanguage) {
		this.messageLanguage = messageLanguage;
	}

	public Boolean getIsInternal() {
		return isInternal;
	}

	public void setIsInternal(Boolean isInternal) {
		this.isInternal = isInternal;
	}

	public String getMessageShowStatus() {
		return messageShowStatus;
	}

	public void setMessageShowStatus(String messageShowStatus) {
		this.messageShowStatus = messageShowStatus;
	}

	public String getMessageType() {
		return messageType;
	}

	public void setMessageType(String messageType) {
		this.messageType = messageType;
	}

	public String getMediaType() {
		return mediaType;
	}

	public void setMediaType(String mediaType) {
		this.mediaType = mediaType;
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

	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}
    
    
}
