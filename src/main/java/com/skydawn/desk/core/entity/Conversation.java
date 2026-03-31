package com.skydawn.desk.core.entity;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 会话表 conversation
 */
@Data
public class Conversation {

    private Long id;
    private Long userId;
    private String officialAccount;
    private String sessionId;
    private String clientId;
    private String channel;
    private String status;
    private OffsetDateTime requestTime;
    private OffsetDateTime closeTime;
    private Integer priority;
    private Long agentId;
    private String agentName;
    private Boolean isTransferAgent;
    private String currentReplyStatus;
    private OffsetDateTime createTime;
    private String createBy;
    private OffsetDateTime updateTime;
    private String updateBy;
    private String delFlag;
    private String remarks;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOfficialAccount() { return officialAccount; }
    public void setOfficialAccount(String officialAccount) { this.officialAccount = officialAccount; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(OffsetDateTime requestTime) { this.requestTime = requestTime; }
    public OffsetDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(OffsetDateTime closeTime) { this.closeTime = closeTime; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public Boolean getIsTransferAgent() { return isTransferAgent; }
    public void setIsTransferAgent(Boolean isTransferAgent) { this.isTransferAgent = isTransferAgent; }
    public String getCurrentReplyStatus() { return currentReplyStatus; }
    public void setCurrentReplyStatus(String currentReplyStatus) { this.currentReplyStatus = currentReplyStatus; }
    public OffsetDateTime getCreateTime() { return createTime; }
    public void setCreateTime(OffsetDateTime createTime) { this.createTime = createTime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public OffsetDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(OffsetDateTime updateTime) { this.updateTime = updateTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
