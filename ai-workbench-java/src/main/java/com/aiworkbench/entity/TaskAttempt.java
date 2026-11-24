package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "task_attempts")
public class TaskAttempt {
    @Id private String attemptId;
    private String taskKey;
    private Integer attemptNo = 1;
    private String status;
    private String startedAt;
    private String finishedAt;
    @Column(length = 4096) private String errorReason;
    private String errorCode;
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String v) { this.attemptId = v; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String v) { this.taskKey = v; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer v) { this.attemptNo = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String v) { this.startedAt = v; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String v) { this.finishedAt = v; }
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String v) { this.errorReason = v; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String v) { this.errorCode = v; }
}
