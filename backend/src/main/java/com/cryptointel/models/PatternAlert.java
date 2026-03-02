package com.cryptointel.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pattern_alerts")
public class PatternAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coin_id", nullable = false)
    private String coinId;

    @Column(name = "pattern_type", nullable = false)
    private String patternType;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "metric_data", columnDefinition = "TEXT")
    private String metricData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
