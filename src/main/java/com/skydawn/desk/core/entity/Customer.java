package com.skydawn.desk.core.entity;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class Customer {

    private String id;

    private String name;

    private String nickName;

    private String whatsappNum;

    private String customerTypeId;

    private OffsetDateTime createdTime;

    private String createdBy;

    private OffsetDateTime updatedTime;

    private String updatedBy;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNickName() {
		return nickName;
	}

	public void setNickName(String nickName) {
		this.nickName = nickName;
	}

	public String getWhatsappNum() {
		return whatsappNum;
	}

	public void setWhatsappNum(String whatsappNum) {
		this.whatsappNum = whatsappNum;
	}

	public String getCustomerTypeId() {
		return customerTypeId;
	}

	public void setCustomerTypeId(String customerTypeId) {
		this.customerTypeId = customerTypeId;
	}

	public OffsetDateTime getCreatedTime() {
		return createdTime;
	}

	public void setCreatedTime(OffsetDateTime createdTime) {
		this.createdTime = createdTime;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public OffsetDateTime getUpdatedTime() {
		return updatedTime;
	}

	public void setUpdatedTime(OffsetDateTime updatedTime) {
		this.updatedTime = updatedTime;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}
}
