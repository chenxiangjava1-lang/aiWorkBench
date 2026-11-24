package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "expected_tasks")
public class ExpectedTask {
    @Id private String taskKey;
    private String assetId;
    private String brandId;
    private String tenantId;
    private String intentId;
    private String platform;
    private String businessDate;
    @Column(columnDefinition = "TEXT") private String question;
    private String analysisDueAt;
    private String priority;
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String v) { this.taskKey = v; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String v) { this.assetId = v; }
    public String getBrandId() { return brandId; }
    public void setBrandId(String v) { this.brandId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public String getIntentId() { return intentId; }
    public void setIntentId(String v) { this.intentId = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { this.platform = v; }
    public String getBusinessDate() { return businessDate; }
    public void setBusinessDate(String v) { this.businessDate = v; }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { this.question = v; }
    public String getAnalysisDueAt() { return analysisDueAt; }
    public void setAnalysisDueAt(String v) { this.analysisDueAt = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
}
