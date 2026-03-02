package com.cryptointel.repositories;

import com.cryptointel.models.UserAlertRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserAlertReadRepository extends JpaRepository<UserAlertRead, Long> {
    List<UserAlertRead> findByUserId(Long userId);

    @Query("SELECT COUNT(a) FROM PatternAlert a WHERE a.id NOT IN " +
           "(SELECT r.alertId FROM UserAlertRead r WHERE r.userId = :userId)")
    long countUnreadByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndAlertId(Long userId, Long alertId);
}
