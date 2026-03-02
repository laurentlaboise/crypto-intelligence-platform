package com.cryptointel.services;

import com.cryptointel.models.MarketSnapshot;
import com.cryptointel.models.PatternAlert;
import com.cryptointel.repositories.MarketSnapshotRepository;
import com.cryptointel.repositories.PatternAlertRepository;
import com.cryptointel.services.ai.AiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PatternDetectionService {

    private static final Logger log = LoggerFactory.getLogger(PatternDetectionService.class);
    private static final int MAX_SNAPSHOTS = 12;

    private final MarketDataService marketDataService;
    private final AiService aiService;
    private final PatternAlertRepository patternAlertRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final ObjectMapper objectMapper;
    private final boolean patternDetectionEnabled;

    private final List<Map<String, Object>> snapshots = new CopyOnWriteArrayList<>();

    public PatternDetectionService(
            MarketDataService marketDataService,
            AiService aiService,
            PatternAlertRepository patternAlertRepository,
            MarketSnapshotRepository marketSnapshotRepository,
            ObjectMapper objectMapper,
            @Value("${app.ai.pattern-detection.enabled:true}") boolean patternDetectionEnabled) {
        this.marketDataService = marketDataService;
        this.aiService = aiService;
        this.patternAlertRepository = patternAlertRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.objectMapper = objectMapper;
        this.patternDetectionEnabled = patternDetectionEnabled;
    }

    @PostConstruct
    public void loadSnapshots() {
        try {
            List<MarketSnapshot> saved = marketSnapshotRepository.findTop12ByOrderByCreatedAtDesc();
            for (int i = saved.size() - 1; i >= 0; i--) {
                List<Map<String, Object>> data = objectMapper.readValue(
                        saved.get(i).getSnapshotData(),
                        new TypeReference<>() {});
                snapshots.add(createSnapshotMap(data, saved.get(i).getCreatedAt()));
            }
            log.info("Loaded {} historical snapshots from database", snapshots.size());
        } catch (Exception e) {
            log.warn("Failed to load historical snapshots: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.pattern-detection.interval-ms:300000}", initialDelay = 60000)
    public void detectPatterns() {
        if (!patternDetectionEnabled) return;

        List<Map<String, Object>> currentData = marketDataService.getTop20();
        if (currentData == null || currentData.isEmpty()) return;

        // Take snapshot
        Map<String, Object> snapshot = createSnapshotMap(currentData, LocalDateTime.now());
        snapshots.add(snapshot);

        // Persist to DB
        persistSnapshot(currentData);

        // Prune in-memory snapshots
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }

        if (snapshots.size() < 2) {
            log.debug("Not enough snapshots for pattern detection yet (have {})", snapshots.size());
            return;
        }

        List<PatternAlert> newAlerts = new ArrayList<>();

        for (Map<String, Object> coin : currentData) {
            String coinId = (String) coin.get("coinId");
            String name = (String) coin.get("name");

            detectVolumeAnomaly(coin, coinId, name, newAlerts);
            detectPriceAcceleration(coin, coinId, name, newAlerts);
            detectAthProximity(coin, coinId, name, newAlerts);
            detectPriceVolumeDivergence(coin, coinId, name, newAlerts);
            detectRankShift(coinId, name, newAlerts);
        }

        if (!newAlerts.isEmpty()) {
            patternAlertRepository.saveAll(newAlerts);
            log.info("Detected {} new pattern alerts", newAlerts.size());

            // Generate AI narratives in batches of 10
            List<Map<String, Object>> batch = new ArrayList<>();
            for (PatternAlert alert : newAlerts) {
                batch.add(Map.of(
                        "coinId", alert.getCoinId(),
                        "patternType", alert.getPatternType(),
                        "title", alert.getTitle(),
                        "metricData", alert.getMetricData() != null ? alert.getMetricData() : ""));
                if (batch.size() == 10) {
                    applyNarratives(newAlerts, batch, batch.size());
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                applyNarratives(newAlerts, batch, batch.size());
            }
        }
    }

    // ========== Detector 1: Volume Anomaly ==========
    private void detectVolumeAnomaly(Map<String, Object> coin, String coinId, String name,
                                      List<PatternAlert> alerts) {
        BigDecimal currentVolume = toBigDecimal(coin.get("totalVolume"));
        if (currentVolume == null || currentVolume.compareTo(BigDecimal.ZERO) == 0) return;

        // Calculate average volume across historical snapshots
        List<BigDecimal> historicalVolumes = new ArrayList<>();
        for (Map<String, Object> snap : snapshots.subList(0, snapshots.size() - 1)) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> coins = (Map<String, Map<String, Object>>) snap.get("coins");
            if (coins != null && coins.containsKey(coinId)) {
                BigDecimal vol = toBigDecimal(coins.get(coinId).get("totalVolume"));
                if (vol != null) historicalVolumes.add(vol);
            }
        }

        if (historicalVolumes.isEmpty()) return;

        BigDecimal avgVolume = historicalVolumes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(historicalVolumes.size()), 2, RoundingMode.HALF_UP);

        if (avgVolume.compareTo(BigDecimal.ZERO) > 0 &&
                currentVolume.compareTo(avgVolume.multiply(new BigDecimal("3"))) > 0) {

            if (isDuplicate(coinId, "VOLUME_ANOMALY")) return;

            BigDecimal ratio = currentVolume.divide(avgVolume, 1, RoundingMode.HALF_UP);
            PatternAlert alert = createAlert(coinId, "VOLUME_ANOMALY", "MEDIUM",
                    name + ": Volume anomaly detected",
                    String.format("{\"currentVolume\":%s,\"avgVolume\":%s,\"ratio\":\"%sx\"}",
                            currentVolume.toPlainString(), avgVolume.toPlainString(), ratio.toPlainString()));
            alerts.add(alert);
        }
    }

    // ========== Detector 2: Price Acceleration ==========
    private void detectPriceAcceleration(Map<String, Object> coin, String coinId, String name,
                                          List<PatternAlert> alerts) {
        BigDecimal change = toBigDecimal(coin.get("priceChangePercentage24h"));
        if (change == null) return;

        if (change.abs().compareTo(new BigDecimal("10")) > 0) {
            if (isDuplicate(coinId, "PRICE_ACCELERATION")) return;

            String severity = change.abs().compareTo(new BigDecimal("20")) > 0 ? "HIGH" : "MEDIUM";
            String direction = change.compareTo(BigDecimal.ZERO) > 0 ? "surge" : "drop";
            PatternAlert alert = createAlert(coinId, "PRICE_ACCELERATION", severity,
                    name + ": Price " + direction + " of " + change.setScale(1, RoundingMode.HALF_UP) + "%",
                    String.format("{\"change24h\":\"%s%%\",\"direction\":\"%s\"}", change.setScale(2, RoundingMode.HALF_UP), direction));
            alerts.add(alert);
        }
    }

    // ========== Detector 3: ATH Proximity ==========
    private void detectAthProximity(Map<String, Object> coin, String coinId, String name,
                                     List<PatternAlert> alerts) {
        BigDecimal currentPrice = toBigDecimal(coin.get("currentPrice"));
        BigDecimal ath = marketDataService.getAth(coinId);
        if (currentPrice == null || ath == null || ath.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal pctFromAth = ath.subtract(currentPrice)
                .divide(ath, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        if (pctFromAth.compareTo(new BigDecimal("5")) <= 0 && pctFromAth.compareTo(BigDecimal.ZERO) >= 0) {
            if (isDuplicate(coinId, "ATH_PROXIMITY")) return;

            PatternAlert alert = createAlert(coinId, "ATH_PROXIMITY", "HIGH",
                    name + ": Within " + pctFromAth.setScale(1, RoundingMode.HALF_UP) + "% of all-time high",
                    String.format("{\"currentPrice\":\"%s\",\"ath\":\"%s\",\"pctFromAth\":\"%s%%\"}",
                            currentPrice.toPlainString(), ath.toPlainString(), pctFromAth.setScale(2, RoundingMode.HALF_UP)));
            alerts.add(alert);
        }
    }

    // ========== Detector 4: Price/Volume Divergence ==========
    private void detectPriceVolumeDivergence(Map<String, Object> coin, String coinId, String name,
                                              List<PatternAlert> alerts) {
        if (snapshots.size() < 2) return;

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> prevCoins =
                (Map<String, Map<String, Object>>) snapshots.get(snapshots.size() - 2).get("coins");
        if (prevCoins == null || !prevCoins.containsKey(coinId)) return;

        BigDecimal currentPrice = toBigDecimal(coin.get("currentPrice"));
        BigDecimal prevPrice = toBigDecimal(prevCoins.get(coinId).get("currentPrice"));
        BigDecimal currentVolume = toBigDecimal(coin.get("totalVolume"));
        BigDecimal prevVolume = toBigDecimal(prevCoins.get(coinId).get("totalVolume"));

        if (currentPrice == null || prevPrice == null || currentVolume == null || prevVolume == null) return;
        if (prevPrice.compareTo(BigDecimal.ZERO) == 0 || prevVolume.compareTo(BigDecimal.ZERO) == 0) return;

        boolean priceUp = currentPrice.compareTo(prevPrice) > 0;
        boolean volumeUp = currentVolume.compareTo(prevVolume) > 0;

        if (priceUp != volumeUp) {
            // Check significance: at least 3% price move
            BigDecimal priceDelta = currentPrice.subtract(prevPrice).abs()
                    .divide(prevPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            if (priceDelta.compareTo(new BigDecimal("3")) < 0) return;

            if (isDuplicate(coinId, "DIVERGENCE")) return;

            String divergenceType = priceUp ? "Price up, volume down" : "Price down, volume up";
            PatternAlert alert = createAlert(coinId, "DIVERGENCE", "LOW",
                    name + ": " + divergenceType,
                    String.format("{\"divergence\":\"%s\",\"priceChange\":\"%s%%\"}",
                            divergenceType, priceDelta.setScale(2, RoundingMode.HALF_UP)));
            alerts.add(alert);
        }
    }

    // ========== Detector 5: Rank Shift ==========
    private void detectRankShift(String coinId, String name, List<PatternAlert> alerts) {
        if (snapshots.size() < 2) return;

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> prevCoins =
                (Map<String, Map<String, Object>>) snapshots.get(snapshots.size() - 2).get("coins");
        if (prevCoins == null || !prevCoins.containsKey(coinId)) return;

        Integer currentRank = marketDataService.getMarketCapRank(coinId);
        Object prevRankObj = prevCoins.get(coinId).get("rank");
        if (currentRank == null || prevRankObj == null) return;

        int prevRank = ((Number) prevRankObj).intValue();
        int shift = Math.abs(currentRank - prevRank);

        if (shift > 5) {
            if (isDuplicate(coinId, "RANK_SHIFT")) return;

            String direction = currentRank < prevRank ? "climbed" : "dropped";
            PatternAlert alert = createAlert(coinId, "RANK_SHIFT", "MEDIUM",
                    name + ": Rank " + direction + " " + shift + " positions",
                    String.format("{\"previousRank\":%d,\"currentRank\":%d,\"shift\":%d,\"direction\":\"%s\"}",
                            prevRank, currentRank, shift, direction));
            alerts.add(alert);
        }
    }

    // ========== Helpers ==========

    private Map<String, Object> createSnapshotMap(List<Map<String, Object>> data, LocalDateTime timestamp) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("timestamp", timestamp.toString());
        Map<String, Map<String, Object>> coins = new HashMap<>();
        for (Map<String, Object> coin : data) {
            String coinId = (String) coin.get("coinId");
            Map<String, Object> coinSnapshot = new HashMap<>();
            coinSnapshot.put("currentPrice", coin.get("currentPrice"));
            coinSnapshot.put("totalVolume", coin.get("totalVolume"));
            coinSnapshot.put("priceChangePercentage24h", coin.get("priceChangePercentage24h"));
            coinSnapshot.put("marketCap", coin.get("marketCap"));
            Integer rank = marketDataService.getMarketCapRank(coinId);
            if (rank != null) coinSnapshot.put("rank", rank);
            coins.put(coinId, coinSnapshot);
        }
        snapshot.put("coins", coins);
        return snapshot;
    }

    private void persistSnapshot(List<Map<String, Object>> data) {
        try {
            MarketSnapshot snapshot = new MarketSnapshot();
            snapshot.setSnapshotData(objectMapper.writeValueAsString(data));
            marketSnapshotRepository.save(snapshot);

            // Prune old snapshots from DB
            List<MarketSnapshot> all = marketSnapshotRepository.findTop12ByOrderByCreatedAtDesc();
            if (all.size() > MAX_SNAPSHOTS) {
                // The query already returns top 12 by desc, so we don't need to delete here normally.
                // But if there are extras due to race conditions, clean them up:
                List<MarketSnapshot> allSnapshots = marketSnapshotRepository.findAll();
                if (allSnapshots.size() > MAX_SNAPSHOTS) {
                    allSnapshots.sort(Comparator.comparing(MarketSnapshot::getCreatedAt).reversed());
                    for (int i = MAX_SNAPSHOTS; i < allSnapshots.size(); i++) {
                        marketSnapshotRepository.delete(allSnapshots.get(i));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to persist snapshot: {}", e.getMessage());
        }
    }

    private PatternAlert createAlert(String coinId, String patternType, String severity,
                                      String title, String metricData) {
        PatternAlert alert = new PatternAlert();
        alert.setCoinId(coinId);
        alert.setPatternType(patternType);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setMetricData(metricData);
        alert.setExpiresAt(LocalDateTime.now().plusHours(24));
        return alert;
    }

    private boolean isDuplicate(String coinId, String patternType) {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        return patternAlertRepository.findByCoinIdAndPatternTypeAndCreatedAtAfter(coinId, patternType, since)
                .stream().findAny().isPresent();
    }

    private void applyNarratives(List<PatternAlert> alerts, List<Map<String, Object>> batch, int batchSize) {
        try {
            List<String> narratives = aiService.generateAlertNarratives(batch);
            int offset = alerts.size() - batchSize;
            for (int i = 0; i < narratives.size() && i < batchSize; i++) {
                PatternAlert alert = alerts.get(offset + i);
                alert.setNarrative(narratives.get(i));
                patternAlertRepository.save(alert);
            }
        } catch (Exception e) {
            log.warn("Failed to generate alert narratives: {}", e.getMessage());
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
