package com.cryptointel.models;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "portfolio_holdings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "coin_id"})
})
public class PortfolioHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coin_id", nullable = false)
    private String coinId;

    @Column(nullable = false, precision = 24, scale = 12)
    private BigDecimal quantity = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCoinId() { return coinId; }
    public void setCoinId(String coinId) { this.coinId = coinId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
