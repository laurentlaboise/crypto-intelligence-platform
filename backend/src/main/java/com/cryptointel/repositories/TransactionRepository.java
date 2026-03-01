package com.cryptointel.repositories;

import com.cryptointel.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Transaction> findByUserIdAndCoinId(Long userId, String coinId);
    List<Transaction> findAllByOrderByCreatedAtDesc();
}
