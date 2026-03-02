package com.cryptointel.dto;

public class PatternAlertResponse {

    private Long id;
    private String coinId;
    private String patternType;
    private String severity;
    private String title;
    private String narrative;
    private String metricData;
    private String createdAt;
    private boolean read;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCoinId() { return coinId; }
    public void setCoinId(String coinId) { this.coinId = coinId; }
    public String getPatternType() { return patternType; }
    public void setPatternType(String patternType) { this.patternType = patternType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public String getMetricData() { return metricData; }
    public void setMetricData(String metricData) { this.metricData = metricData; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
