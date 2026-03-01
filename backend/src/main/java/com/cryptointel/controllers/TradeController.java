package com.cryptointel.controllers;

import com.cryptointel.services.TradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradeController {

    private final TradingService tradingService;

    public TradeController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/trade")
    public ResponseEntity<?> executeTrade(@RequestBody Map<String, Object> body,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        String coinId = (String) body.get("coinId");
        String action = (String) body.get("action");
        Object amountObj = body.get("amountUsd");

        if (coinId == null || action == null || amountObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "coinId, action, and amountUsd are required"));
        }

        BigDecimal amountUsd;
        try {
            amountUsd = new BigDecimal(amountObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid amountUsd value"));
        }

        if (amountUsd.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "amountUsd must be positive"));
        }

        if (!"BUY".equals(action) && !"SELL".equals(action)) {
            return ResponseEntity.badRequest().body(Map.of("message", "action must be BUY or SELL"));
        }

        try {
            Map<String, Object> result = tradingService.executeTrade(userId, coinId, action, amountUsd);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolio(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(tradingService.getPortfolio(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(tradingService.getUserTransactions(userId));
    }
}
