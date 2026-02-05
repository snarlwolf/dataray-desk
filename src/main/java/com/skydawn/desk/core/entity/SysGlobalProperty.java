package com.skydawn.desk.core.entity;


import java.time.OffsetDateTime;

/**
 * 全局属性配置实体类
 * 对应表：sys_global_property
 */
public class SysGlobalProperty {
    
    /** 主键ID（数据库自增） */
    private Long id;
    
    /** 系统名称：来自/用于哪个系统或平台 */
    private String systemName;
    
    /** 分类 */
    private String category;
    
    /** 参数名 */
    private String propKey;
    
    /** 参数值 */
    private String propValue;
    
    /** 是否密文：0-原文，1-密文 */
    private Integer isCipher;
    
    /** 说明 */
    private String remark;
    
    /** 创建时间 */
    private OffsetDateTime createdTime;
    
    /** 创建人 */
    private String createdBy;
    
    /** 更新时间 */
    private OffsetDateTime updatedTime;
    
    /** 更新人 */
    private String updatedBy;

    public SysGlobalProperty() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPropKey() {
        return propKey;
    }

    public void setPropKey(String propKey) {
        this.propKey = propKey;
    }

    public String getPropValue() {
        return propValue;
    }

    public void setPropValue(String propValue) {
        this.propValue = propValue;
    }

    public Integer getIsCipher() {
        return isCipher;
    }

    public void setIsCipher(Integer isCipher) {
        this.isCipher = isCipher;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    @Override
    public String toString() {
        return "SysGlobalProperty{" +
                "id=" + id +
                ", systemName='" + systemName + '\'' +
                ", category='" + category + '\'' +
                ", propKey='" + propKey + '\'' +
                ", propValue='" + propValue + '\'' +
                ", isCipher=" + isCipher +
                ", remark='" + remark + '\'' +
                ", createdTime=" + createdTime +
                ", createdBy='" + createdBy + '\'' +
                ", updatedTime=" + updatedTime +
                ", updatedBy='" + updatedBy + '\'' +
                '}';
    }
}
