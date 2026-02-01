package com.skydawn.desk.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("cs_users")
public class CsUsers {

    private Long id;                 // Snowflake

    private UUID userNo;             // UUID 冗余编号

    private Long tenantId;

    private String userName;
    private String whatsappNum;
    private String nickName;
    private String avatarUrl;

    private String userType;
    private String userLevel;

    private String remark;

    private OffsetDateTime createTime;
    private String createBy;

    private OffsetDateTime updateTime;
    private String updateBy;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public UUID getUserNo() {
		return userNo;
	}
	public void setUserNo(UUID userNo) {
		this.userNo = userNo;
	}
	public Long getTenantId() {
		return tenantId;
	}
	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getWhatsappNum() {
		return whatsappNum;
	}
	public void setWhatsappNum(String whatsappNum) {
		this.whatsappNum = whatsappNum;
	}
	public String getNickName() {
		return nickName;
	}
	public void setNickName(String nickName) {
		this.nickName = nickName;
	}
	public String getAvatarUrl() {
		return avatarUrl;
	}
	public void setAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}
	public String getUserType() {
		return userType;
	}
	public void setUserType(String userType) {
		this.userType = userType;
	}
	public String getUserLevel() {
		return userLevel;
	}
	public void setUserLevel(String userLevel) {
		this.userLevel = userLevel;
	}
	public String getRemark() {
		return remark;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	public OffsetDateTime getCreateTime() {
		return createTime;
	}
	public void setCreateTime(OffsetDateTime createTime) {
		this.createTime = createTime;
	}
	public String getCreateBy() {
		return createBy;
	}
	public void setCreateBy(String createBy) {
		this.createBy = createBy;
	}
	public OffsetDateTime getUpdateTime() {
		return updateTime;
	}
	public void setUpdateTime(OffsetDateTime updateTime) {
		this.updateTime = updateTime;
	}
	public String getUpdateBy() {
		return updateBy;
	}
	public void setUpdateBy(String updateBy) {
		this.updateBy = updateBy;
	}
    
    
}
