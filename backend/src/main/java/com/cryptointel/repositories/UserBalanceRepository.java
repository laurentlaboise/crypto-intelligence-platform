package com.cryptointel.repositories;

import com.cryptointel.models.UserBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBalanceRepository extends JpaRepository<UserBalance, Long> {
}
