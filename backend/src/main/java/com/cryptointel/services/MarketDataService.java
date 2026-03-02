package com.cryptointel.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final String baseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile List<Map<String, Object>> cachedMarketData = new ArrayList<>();
    private final Map<String, BigDecimal> priceCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> priceChange24hCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> athCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> marketCapRankCache = new ConcurrentHashMap<>();

    public MarketDataService(@Value("${app.coingecko.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Scheduled(fixedDelayString = "${app.coingecko.cache-duration-seconds:30}000", initialDelay = 0)
    public void refreshMarketData() {
        try {
            String url = baseUrl + "/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=20&page=1&sparkline=false";
            Object[] coins = restTemplate.getForObject(url, Object[].class);

            if (coins != null) {
                List<Map<String, Object>> marketData = new ArrayList<>();
                for (Object coin : coins) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> coinMap = (Map<String, Object>) coin;
                    Map<String, Object> filtered = new LinkedHashMap<>();
                    filtered.put("coinId", coinMap.get("id"));
                    filtered.put("symbol", coinMap.get("symbol"));
                    filtered.put("name", coinMap.get("name"));

                    Object price = coinMap.get("current_price");
                    filtered.put("currentPrice", price);
                    filtered.put("priceChangePercentage24h", coinMap.get("price_change_percentage_24h"));
                    filtered.put("marketCap", coinMap.get("market_cap"));
                    filtered.put("totalVolume", coinMap.get("total_volume"));

                    marketData.add(filtered);

                    String id = (String) coinMap.get("id");
                    if (price != null) {
                        priceCache.put(id, new BigDecimal(price.toString()));
                    }
                    Object pctChange = coinMap.get("price_change_percentage_24h");
                    if (pctChange != null) {
                        priceChange24hCache.put(id, new BigDecimal(pctChange.toString()));
                    }
                    Object ath = coinMap.get("ath");
                    if (ath != null) {
                        athCache.put(id, new BigDecimal(ath.toString()));
                    }
                    Object rank = coinMap.get("market_cap_rank");
                    if (rank != null) {
                        marketCapRankCache.put(id, ((Number) rank).intValue());
                    }
                }
                cachedMarketData = marketData;
                log.debug("Market data refreshed: {} coins", marketData.size());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh market data: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getTop20() {
        return cachedMarketData;
    }

    public BigDecimal getCurrentPrice(String coinId) {
        return priceCache.get(coinId);
    }

    public BigDecimal getPriceChangePercentage24h(String coinId) {
        return priceChange24hCache.get(coinId);
    }

    public BigDecimal getAth(String coinId) {
        return athCache.get(coinId);
    }

    public Integer getMarketCapRank(String coinId) {
        return marketCapRankCache.get(coinId);
    }

    public List<Map<String, Object>> getPriceHistory(String coinId, int days) {
        try {
            String url = baseUrl + "/coins/" + coinId + "/market_chart?vs_currency=usd&days=" + days;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("prices")) {
                @SuppressWarnings("unchecked")
                List<List<Object>> prices = (List<List<Object>>) response.get("prices");
                List<Map<String, Object>> result = new ArrayList<>();
                for (List<Object> point : prices) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("timestamp", point.get(0));
                    entry.put("price", point.get(1));
                    result.add(entry);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch price history for {}: {}", coinId, e.getMessage());
        }
        return List.of();
    }
}
