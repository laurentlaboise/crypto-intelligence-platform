package com.cryptointel.repositories;

import com.cryptointel.models.MarketSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, Long> {
    List<MarketSnapshot> findTop12ByOrderByCreatedAtDesc();
}
