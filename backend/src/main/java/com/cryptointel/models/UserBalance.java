package com.cryptointel.models;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "user_balances")
public class UserBalance {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
