package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "metric_snapshots")
public class MetricSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer snapshotId;
    private String brandId;
    private String platform;
    private String businessDate;
    private Integer mentionedCount;
    private Integer recommendedCount;
    private Integer totalTasks;
    private String snapshotTakenAt;
    public Integer getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Integer v) { this.snapshotId = v; }
    public String getBrandId() { return brandId; }
    public void setBrandId(String v) { this.brandId = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { this.platform = v; }
    public String getBusinessDate() { return businessDate; }
    public void setBusinessDate(String v) { this.businessDate = v; }
    public Integer getMentionedCount() { return mentionedCount; }
    public void setMentionedCount(Integer v) { this.mentionedCount = v; }
    public Integer getRecommendedCount() { return recommendedCount; }
    public void setRecommendedCount(Integer v) { this.recommendedCount = v; }
    public Integer getTotalTasks() { return totalTasks; }
    public void setTotalTasks(Integer v) { this.totalTasks = v; }
    public String getSnapshotTakenAt() { return snapshotTakenAt; }
    public void setSnapshotTakenAt(String v) { this.snapshotTakenAt = v; }
}
