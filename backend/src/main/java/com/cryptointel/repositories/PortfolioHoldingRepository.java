package com.cryptointel.repositories;

import com.cryptointel.models.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    List<PortfolioHolding> findByUserId(Long userId);
    Optional<PortfolioHolding> findByUserIdAndCoinId(Long userId, String coinId);
}
