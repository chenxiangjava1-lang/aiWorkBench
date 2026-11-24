package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "analysis_results")
public class AnalysisResult {
    @Id private String resultId;
    private String taskKey;
    private String recordId;
    @Column(columnDefinition = "TEXT") private String summary;
    private String brandMentioned;
    private String recommended;
    @Column(columnDefinition = "TEXT") private String evidenceText;
    @Column(columnDefinition = "TEXT") private String citationUrls;
    @Column(columnDefinition = "TEXT") private String riskFlags;
    private String analysisSource;
    private String analyzedAt;
    private Integer isValid = 1;
    private String validationNote;
    public String getResultId() { return resultId; }
    public void setResultId(String v) { this.resultId = v; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String v) { this.taskKey = v; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String v) { this.recordId = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public String getBrandMentioned() { return brandMentioned; }
    public void setBrandMentioned(String v) { this.brandMentioned = v; }
    public String getRecommended() { return recommended; }
    public void setRecommended(String v) { this.recommended = v; }
    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String v) { this.evidenceText = v; }
    public String getCitationUrls() { return citationUrls; }
    public void setCitationUrls(String v) { this.citationUrls = v; }
    public String getRiskFlags() { return riskFlags; }
    public void setRiskFlags(String v) { this.riskFlags = v; }
    public String getAnalysisSource() { return analysisSource; }
    public void setAnalysisSource(String v) { this.analysisSource = v; }
    public String getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(String v) { this.analyzedAt = v; }
    public Integer getIsValid() { return isValid; }
    public void setIsValid(Integer v) { this.isValid = v; }
    public String getValidationNote() { return validationNote; }
    public void setValidationNote(String v) { this.validationNote = v; }
}
