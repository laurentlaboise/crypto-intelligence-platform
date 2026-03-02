package com.cryptointel.repositories;

import com.cryptointel.models.PatternAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PatternAlertRepository extends JpaRepository<PatternAlert, Long> {
    List<PatternAlert> findTop50ByOrderByCreatedAtDesc();
    List<PatternAlert> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
    List<PatternAlert> findByCoinIdAndPatternTypeAndCreatedAtAfter(String coinId, String patternType, LocalDateTime since);
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
