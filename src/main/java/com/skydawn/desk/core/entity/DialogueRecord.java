package com.skydawn.desk.core.entity;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class DialogueRecord {

    private String id;

    private Boolean staffOrCustomer;

    private String chatterId;

    private String message;

    private String sessionId;

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

	public Boolean getStaffOrCustomer() {
		return staffOrCustomer;
	}

	public void setStaffOrCustomer(Boolean staffOrCustomer) {
		this.staffOrCustomer = staffOrCustomer;
	}

	public String getChatterId() {
		return chatterId;
	}

	public void setChatterId(String chatterId) {
		this.chatterId = chatterId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
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
