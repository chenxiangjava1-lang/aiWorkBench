package com.aiworkbench.entity;

import jakarta.persistence.*;

@Entity @Table(name = "assets")
public class Asset {
    @Id private String assetId;
    private String assetType;
    private String assetName;
    private String brandId;
    private String brandName;
    private String industry;
    private String officialDomain;
    private String referenceClaim;
    private Integer active = 1;
    private String tenantId;
    public String getAssetId() { return assetId; }
    public void setAssetId(String v) { this.assetId = v; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String v) { this.assetType = v; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String v) { this.assetName = v; }
    public String getBrandId() { return brandId; }
    public void setBrandId(String v) { this.brandId = v; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String v) { this.brandName = v; }
    public String getIndustry() { return industry; }
    public void setIndustry(String v) { this.industry = v; }
    public String getOfficialDomain() { return officialDomain; }
    public void setOfficialDomain(String v) { this.officialDomain = v; }
    public String getReferenceClaim() { return referenceClaim; }
    public void setReferenceClaim(String v) { this.referenceClaim = v; }
    public Integer getActive() { return active; }
    public void setActive(Integer v) { this.active = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
}
