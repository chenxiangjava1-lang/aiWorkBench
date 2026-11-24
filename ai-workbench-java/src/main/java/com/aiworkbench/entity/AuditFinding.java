package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "audit_findings")
public class AuditFinding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer findingId;
    private String taskKey;
    private String anomalyType;
    @Column(columnDefinition = "TEXT") private String description;
    private String detectedAt;
    private Integer resolved = 0;
    private String resolvedAt;
    private String resolutionNote;
    public Integer getFindingId() { return findingId; }
    public void setFindingId(Integer v) { this.findingId = v; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String v) { this.taskKey = v; }
    public String getAnomalyType() { return anomalyType; }
    public void setAnomalyType(String v) { this.anomalyType = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getDetectedAt() { return detectedAt; }
    public void setDetectedAt(String v) { this.detectedAt = v; }
    public Integer getResolved() { return resolved; }
    public void setResolved(Integer v) { this.resolved = v; }
    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String v) { this.resolvedAt = v; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String v) { this.resolutionNote = v; }
}
