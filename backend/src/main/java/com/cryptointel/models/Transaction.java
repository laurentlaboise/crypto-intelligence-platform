package com.cryptointel.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coin_id", nullable = false)
    private String coinId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Action action;

    @Column(name = "amount_usd", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "execution_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal executionPrice;

    @Column(nullable = false, precision = 24, scale = 12)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Action {
        BUY, SELL
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCoinId() { return coinId; }
    public void setCoinId(String coinId) { this.coinId = coinId; }
    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }
    public BigDecimal getAmountUsd() { return amountUsd; }
    public void setAmountUsd(BigDecimal amountUsd) { this.amountUsd = amountUsd; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public void setExecutionPrice(BigDecimal executionPrice) { this.executionPrice = executionPrice; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
