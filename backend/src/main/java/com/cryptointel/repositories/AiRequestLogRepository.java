package com.cryptointel.repositories;

import com.cryptointel.models.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {

    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
