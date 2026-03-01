package com.cryptointel.services;

import com.cryptointel.models.PortfolioHolding;
import com.cryptointel.models.Transaction;
import com.cryptointel.models.UserBalance;
import com.cryptointel.repositories.PortfolioHoldingRepository;
import com.cryptointel.repositories.TransactionRepository;
import com.cryptointel.repositories.UserBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class TradingService {

    private final TransactionRepository transactionRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final UserBalanceRepository balanceRepository;
    private final MarketDataService marketDataService;

    public TradingService(TransactionRepository transactionRepository,
                          PortfolioHoldingRepository holdingRepository,
                          UserBalanceRepository balanceRepository,
                          MarketDataService marketDataService) {
        this.transactionRepository = transactionRepository;
        this.holdingRepository = holdingRepository;
        this.balanceRepository = balanceRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public Map<String, Object> executeTrade(Long userId, String coinId, String action, BigDecimal amountUsd) {
        BigDecimal currentPrice = marketDataService.getCurrentPrice(coinId);
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unable to determine current price for " + coinId);
        }

        UserBalance userBalance = balanceRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User balance not found"));

        BigDecimal quantity = amountUsd.divide(currentPrice, 12, RoundingMode.HALF_UP);

        if ("BUY".equals(action)) {
            if (userBalance.getBalance().compareTo(amountUsd) < 0) {
                throw new IllegalArgumentException("Insufficient balance");
            }
            userBalance.setBalance(userBalance.getBalance().subtract(amountUsd));

            PortfolioHolding holding = holdingRepository.findByUserIdAndCoinId(userId, coinId)
                    .orElseGet(() -> {
                        PortfolioHolding h = new PortfolioHolding();
                        h.setUserId(userId);
                        h.setCoinId(coinId);
                        h.setQuantity(BigDecimal.ZERO);
                        return h;
                    });
            holding.setQuantity(holding.getQuantity().add(quantity));
            holdingRepository.save(holding);

        } else if ("SELL".equals(action)) {
            PortfolioHolding holding = holdingRepository.findByUserIdAndCoinId(userId, coinId)
                    .orElseThrow(() -> new IllegalArgumentException("Insufficient holdings"));

            if (holding.getQuantity().compareTo(quantity) < 0) {
                throw new IllegalArgumentException("Insufficient holdings");
            }

            holding.setQuantity(holding.getQuantity().subtract(quantity));
            if (holding.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                holdingRepository.delete(holding);
            } else {
                holdingRepository.save(holding);
            }

            userBalance.setBalance(userBalance.getBalance().add(amountUsd));
        } else {
            throw new IllegalArgumentException("Invalid action: must be BUY or SELL");
        }

        balanceRepository.save(userBalance);

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setCoinId(coinId);
        tx.setAction(Transaction.Action.valueOf(action));
        tx.setAmountUsd(amountUsd);
        tx.setExecutionPrice(currentPrice);
        tx.setQuantity(quantity);
        tx = transactionRepository.save(tx);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", tx.getId());
        result.put("coinId", coinId);
        result.put("action", action);
        result.put("executionPrice", currentPrice);
        result.put("quantity", quantity);
        result.put("updatedBalance", userBalance.getBalance());
        result.put("timestamp", tx.getCreatedAt().toString());
        return result;
    }

    public Map<String, Object> getPortfolio(Long userId) {
        UserBalance userBalance = balanceRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User balance not found"));

        List<PortfolioHolding> holdings = holdingRepository.findByUserId(userId);
        BigDecimal totalHoldingsValue = BigDecimal.ZERO;
        List<Map<String, Object>> holdingsList = new ArrayList<>();

        for (PortfolioHolding holding : holdings) {
            BigDecimal price = marketDataService.getCurrentPrice(holding.getCoinId());
            if (price == null) price = BigDecimal.ZERO;

            BigDecimal currentValue = holding.getQuantity().multiply(price);
            totalHoldingsValue = totalHoldingsValue.add(currentValue);

            // Calculate P/L from transactions
            List<com.cryptointel.models.Transaction> txs =
                    transactionRepository.findByUserIdAndCoinId(userId, holding.getCoinId());
            BigDecimal totalSpent = BigDecimal.ZERO;
            BigDecimal totalSold = BigDecimal.ZERO;
            for (Transaction tx : txs) {
                if (tx.getAction() == Transaction.Action.BUY) {
                    totalSpent = totalSpent.add(tx.getAmountUsd());
                } else {
                    totalSold = totalSold.add(tx.getAmountUsd());
                }
            }
            BigDecimal costBasis = totalSpent.subtract(totalSold);
            BigDecimal plPct = BigDecimal.ZERO;
            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                plPct = currentValue.subtract(costBasis)
                        .divide(costBasis, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            Map<String, Object> h = new LinkedHashMap<>();
            h.put("coinId", holding.getCoinId());
            h.put("quantity", holding.getQuantity());
            h.put("currentValue", currentValue.setScale(2, RoundingMode.HALF_UP));
            h.put("profitLossPercentage", plPct.setScale(2, RoundingMode.HALF_UP));
            holdingsList.add(h);
        }

        BigDecimal totalPortfolioValue = userBalance.getBalance().add(totalHoldingsValue);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentBalance", userBalance.getBalance().setScale(2, RoundingMode.HALF_UP));
        result.put("totalPortfolioValue", totalPortfolioValue.setScale(2, RoundingMode.HALF_UP));
        result.put("holdings", holdingsList);
        return result;
    }

    public List<Map<String, Object>> getUserTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapTransaction)
                .toList();
    }

    public List<Map<String, Object>> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapTransaction)
                .toList();
    }

    private Map<String, Object> mapTransaction(Transaction tx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transactionId", tx.getId());
        m.put("coinId", tx.getCoinId());
        m.put("action", tx.getAction().name());
        m.put("amountUsd", tx.getAmountUsd());
        m.put("executionPrice", tx.getExecutionPrice());
        m.put("quantity", tx.getQuantity());
        m.put("timestamp", tx.getCreatedAt().toString());
        return m;
    }
}
