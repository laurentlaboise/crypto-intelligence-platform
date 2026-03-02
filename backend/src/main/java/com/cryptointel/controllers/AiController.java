package com.cryptointel.controllers;

import com.cryptointel.dto.MarketAnalysisResponse;
import com.cryptointel.dto.PatternAlertResponse;
import com.cryptointel.dto.PortfolioRiskResponse;
import com.cryptointel.dto.TradeReasoningResponse;
import com.cryptointel.services.ai.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/analysis/{coinId}")
    public ResponseEntity<?> getMarketAnalysis(@PathVariable String coinId,
                                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        MarketAnalysisResponse response = aiService.getMarketAnalysis(coinId, userId);
        if (response == null) {
            return ResponseEntity.ok(Map.of("available", false, "message", "AI analysis temporarily unavailable"));
        }
        return ResponseEntity.ok(Map.of("available", true, "data", response));
    }

    @GetMapping("/portfolio-risk")
    public ResponseEntity<?> getPortfolioRisk(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PortfolioRiskResponse response = aiService.getPortfolioRisk(userId);
        if (response == null) {
            return ResponseEntity.ok(Map.of("available", false, "message", "Portfolio risk assessment unavailable"));
        }
        return ResponseEntity.ok(Map.of("available", true, "data", response));
    }

    @GetMapping("/trade-reasoning/{coinId}/{action}")
    public ResponseEntity<?> getTradeReasoning(@PathVariable String coinId,
                                                @PathVariable String action,
                                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TradeReasoningResponse response = aiService.getTradeReasoning(coinId, action, userId);
        if (response == null) {
            return ResponseEntity.ok(Map.of("available", false, "message", "Trade reasoning temporarily unavailable"));
        }
        return ResponseEntity.ok(Map.of("available", true, "data", response));
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> getAlerts(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<PatternAlertResponse> alerts = aiService.getAlerts(userId);
        return ResponseEntity.ok(Map.of("available", true, "data", alerts));
    }

    @GetMapping("/alerts/unread-count")
    public ResponseEntity<?> getUnreadAlertCount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        long count = aiService.getUnreadAlertCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/alerts/mark-read")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> markAlertsRead(@RequestBody Map<String, Object> body,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<Number> alertIds = (List<Number>) body.get("alertIds");
        if (alertIds == null || alertIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "alertIds required"));
        }
        List<Long> ids = alertIds.stream().map(Number::longValue).toList();
        aiService.markAlertsRead(userId, ids);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, String> status = aiService.checkProviderHealth();
        return ResponseEntity.ok(status);
    }
}
