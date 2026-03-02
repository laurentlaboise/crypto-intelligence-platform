package com.cryptointel.dto;

import java.math.BigDecimal;

public class PortfolioRiskResponse {

    private BigDecimal hhiIndex;
    private BigDecimal topHoldingPercentage;
    private String topHoldingCoinId;
    private BigDecimal weightedVolatility;

    private int riskScore;
    private String riskJustification;
    private String concentrationNarrative;
    private String volatilityNarrative;
    private String actionableInsight;

    public BigDecimal getHhiIndex() { return hhiIndex; }
    public void setHhiIndex(BigDecimal hhiIndex) { this.hhiIndex = hhiIndex; }
    public BigDecimal getTopHoldingPercentage() { return topHoldingPercentage; }
    public void setTopHoldingPercentage(BigDecimal topHoldingPercentage) { this.topHoldingPercentage = topHoldingPercentage; }
    public String getTopHoldingCoinId() { return topHoldingCoinId; }
    public void setTopHoldingCoinId(String topHoldingCoinId) { this.topHoldingCoinId = topHoldingCoinId; }
    public BigDecimal getWeightedVolatility() { return weightedVolatility; }
    public void setWeightedVolatility(BigDecimal weightedVolatility) { this.weightedVolatility = weightedVolatility; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getRiskJustification() { return riskJustification; }
    public void setRiskJustification(String riskJustification) { this.riskJustification = riskJustification; }
    public String getConcentrationNarrative() { return concentrationNarrative; }
    public void setConcentrationNarrative(String concentrationNarrative) { this.concentrationNarrative = concentrationNarrative; }
    public String getVolatilityNarrative() { return volatilityNarrative; }
    public void setVolatilityNarrative(String volatilityNarrative) { this.volatilityNarrative = volatilityNarrative; }
    public String getActionableInsight() { return actionableInsight; }
    public void setActionableInsight(String actionableInsight) { this.actionableInsight = actionableInsight; }
}
