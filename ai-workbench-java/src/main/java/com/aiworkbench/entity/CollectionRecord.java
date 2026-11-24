package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "collection_records")
public class CollectionRecord {
    @Id private String recordId;
    private String taskKey;
    private String attemptId;
    private String connectorId;
    @Column(columnDefinition = "TEXT") private String rawAnswer;
    private String sourceUrl;
    private String collectedAt;
    public String getRecordId() { return recordId; }
    public void setRecordId(String v) { this.recordId = v; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String v) { this.taskKey = v; }
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String v) { this.attemptId = v; }
    public String getConnectorId() { return connectorId; }
    public void setConnectorId(String v) { this.connectorId = v; }
    public String getRawAnswer() { return rawAnswer; }
    public void setRawAnswer(String v) { this.rawAnswer = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public String getCollectedAt() { return collectedAt; }
    public void setCollectedAt(String v) { this.collectedAt = v; }
}
