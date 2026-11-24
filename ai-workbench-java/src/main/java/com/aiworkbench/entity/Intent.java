package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "intents")
public class Intent {
    @Id private String intentId;
    private String assetId;
    private String intentType;
    @Column(columnDefinition = "TEXT") private String question;
    private String scenario;
    private String language;
    public String getIntentId() { return intentId; }
    public void setIntentId(String v) { this.intentId = v; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String v) { this.assetId = v; }
    public String getIntentType() { return intentType; }
    public void setIntentType(String v) { this.intentType = v; }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { this.question = v; }
    public String getScenario() { return scenario; }
    public void setScenario(String v) { this.scenario = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }
}
