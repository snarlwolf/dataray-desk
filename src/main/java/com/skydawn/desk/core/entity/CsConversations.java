package com.skydawn.desk.core.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("cs_conversations")
public class CsConversations {

    @TableField("W")
    private UUID id;              // 原字段名 "W"，UUID 主键

    private Long tenantId;
    private Long userId;

    private String channel;
    private String status;
    private String priority;

    private Boolean isReplyed;

    private OffsetDateTime lastMessageAt;
    private String lastMessageText;

    private OffsetDateTime createTime;
    private OffsetDateTime updateTime;

    private Long assignee;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getPriority() {
		return priority;
	}

	public void setPriority(String priority) {
		this.priority = priority;
	}

	public Boolean getIsReplyed() {
		return isReplyed;
	}

	public void setIsReplyed(Boolean isReplyed) {
		this.isReplyed = isReplyed;
	}

	public OffsetDateTime getLastMessageAt() {
		return lastMessageAt;
	}

	public void setLastMessageAt(OffsetDateTime lastMessageAt) {
		this.lastMessageAt = lastMessageAt;
	}

	public String getLastMessageText() {
		return lastMessageText;
	}

	public void setLastMessageText(String lastMessageText) {
		this.lastMessageText = lastMessageText;
	}

	public OffsetDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(OffsetDateTime createTime) {
		this.createTime = createTime;
	}

	public OffsetDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(OffsetDateTime updateTime) {
		this.updateTime = updateTime;
	}

	public Long getAssignee() {
		return assignee;
	}

	public void setAssignee(Long assignee) {
		this.assignee = assignee;
	}
    
}
