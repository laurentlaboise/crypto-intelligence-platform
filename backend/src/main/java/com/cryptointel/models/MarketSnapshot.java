package com.cryptointel.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_snapshots")
public class MarketSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "TEXT")
    private String snapshotData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSnapshotData() { return snapshotData; }
    public void setSnapshotData(String snapshotData) { this.snapshotData = snapshotData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
