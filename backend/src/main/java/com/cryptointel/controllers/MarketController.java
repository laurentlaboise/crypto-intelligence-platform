package com.cryptointel.controllers;

import com.cryptointel.services.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketDataService;

    public MarketController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/top20")
    public ResponseEntity<List<Map<String, Object>>> getTop20() {
        return ResponseEntity.ok(marketDataService.getTop20());
    }

    @GetMapping("/history/{coinId}")
    public ResponseEntity<List<Map<String, Object>>> getPriceHistory(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "1") int days) {
        return ResponseEntity.ok(marketDataService.getPriceHistory(coinId, days));
    }
}
