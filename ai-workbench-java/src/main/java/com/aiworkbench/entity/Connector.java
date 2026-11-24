package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "connectors")
public class Connector {
    @Id private String connectorId;
    private String platform;
    private String connectorType;
    private String collectionMode;
    private String endpoint;
    private String authMode;
    private Integer rateLimitPerMinute;
    private Integer enabled = 1;
    private String note;
    public String getConnectorId() { return connectorId; }
    public void setConnectorId(String v) { this.connectorId = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { this.platform = v; }
    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String v) { this.connectorType = v; }
    public String getCollectionMode() { return collectionMode; }
    public void setCollectionMode(String v) { this.collectionMode = v; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String v) { this.endpoint = v; }
    public String getAuthMode() { return authMode; }
    public void setAuthMode(String v) { this.authMode = v; }
    public Integer getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(Integer v) { this.rateLimitPerMinute = v; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer v) { this.enabled = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
}
