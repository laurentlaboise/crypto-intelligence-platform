package com.cryptointel.services.ai;

import com.cryptointel.dto.*;
import com.cryptointel.models.AiRequestLog;
import com.cryptointel.models.AiUsage;
import com.cryptointel.models.PatternAlert;
import com.cryptointel.models.UserAlertRead;
import com.cryptointel.repositories.AiRequestLogRepository;
import com.cryptointel.repositories.AiUsageRepository;
import com.cryptointel.repositories.PatternAlertRepository;
import com.cryptointel.repositories.UserAlertReadRepository;
import com.cryptointel.services.MarketDataService;
import com.cryptointel.services.TradingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String PROMPT_VERSION = "v1";

    private final GeminiProvider geminiProvider;
    private final ClaudeProvider claudeSonnet;
    private final ClaudeProvider claudeHaiku;
    private final MarketDataService marketDataService;
    private final TradingService tradingService;
    private final AiUsageRepository aiUsageRepository;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final PatternAlertRepository patternAlertRepository;
    private final UserAlertReadRepository userAlertReadRepository;
    private final ObjectMapper objectMapper;

    private final boolean aiEnabled;
    private final int dailyTokensPerUser;

    private final TtlCache<MarketAnalysisResponse> marketAnalysisCache;
    private final TtlCache<PortfolioRiskResponse> portfolioRiskCache;
    private final TtlCache<TradeReasoningResponse> tradeReasoningCache;

    public AiService(
            GeminiProvider geminiProvider,
            @Qualifier("claudeSonnet") ClaudeProvider claudeSonnet,
            @Qualifier("claudeHaiku") ClaudeProvider claudeHaiku,
            MarketDataService marketDataService,
            TradingService tradingService,
            AiUsageRepository aiUsageRepository,
            AiRequestLogRepository aiRequestLogRepository,
            PatternAlertRepository patternAlertRepository,
            UserAlertReadRepository userAlertReadRepository,
            ObjectMapper objectMapper,
            @Value("${app.ai.enabled:false}") boolean aiEnabled,
            @Value("${app.ai.budget.daily-tokens-per-user:100000}") int dailyTokensPerUser,
            @Value("${app.ai.cache.market-analysis-ttl-minutes:15}") int marketTtl,
            @Value("${app.ai.cache.portfolio-risk-ttl-minutes:10}") int portfolioTtl,
            @Value("${app.ai.cache.trade-reasoning-ttl-minutes:5}") int tradeTtl) {

        this.geminiProvider = geminiProvider;
        this.claudeSonnet = claudeSonnet;
        this.claudeHaiku = claudeHaiku;
        this.marketDataService = marketDataService;
        this.tradingService = tradingService;
        this.aiUsageRepository = aiUsageRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.patternAlertRepository = patternAlertRepository;
        this.userAlertReadRepository = userAlertReadRepository;
        this.objectMapper = objectMapper;
        this.aiEnabled = aiEnabled;
        this.dailyTokensPerUser = dailyTokensPerUser;

        this.marketAnalysisCache = new TtlCache<>(marketTtl * 60_000L);
        this.portfolioRiskCache = new TtlCache<>(portfolioTtl * 60_000L);
        this.tradeReasoningCache = new TtlCache<>(tradeTtl * 60_000L);
    }

    // ========== Feature 1: Market Analysis ==========

    public MarketAnalysisResponse getMarketAnalysis(String coinId, Long userId) {
        if (!aiEnabled) return null;

        String cacheKey = PROMPT_VERSION + ":market:" + coinId;
        MarketAnalysisResponse cached = marketAnalysisCache.get(cacheKey);
        if (cached != null) return cached;

        if (!checkBudget(userId, 500)) return null;

        Map<String, Object> coinData = findCoinData(coinId);
        if (coinData == null) return null;

        String prompt = buildMarketAnalysisPrompt(coinData);
        long start = System.currentTimeMillis();

        try {
            String rawResponse = geminiProvider.generate(prompt, 500);
            long duration = System.currentTimeMillis() - start;

            String json = extractJson(rawResponse);
            MarketAnalysisResponse response = objectMapper.readValue(json, MarketAnalysisResponse.class);

            if (response != null) {
                marketAnalysisCache.put(cacheKey, response);
                recordUsage(userId, 500, "market-analysis", geminiProvider.getProviderName(), (int) duration);
            }
            return response;
        } catch (Exception e) {
            log.warn("Market analysis failed for {}: {}", coinId, e.getMessage());
            return null;
        }
    }

    private String buildMarketAnalysisPrompt(Map<String, Object> coinData) {
        return String.format("""
            ROLE: You are a cryptocurrency market analyst providing data-driven assessments. \
            You never fabricate data or reference information not provided to you.

            TASK: Analyze the current market data for %s and provide a structured assessment.

            DATA:
            - Coin: %s (%s)
            - Current Price: $%s
            - 24h Price Change: %s%%
            - Market Cap: $%s
            - 24h Trading Volume: $%s

            INSTRUCTIONS:
            1. Write a 2-sentence summary of the current market position.
            2. Identify up to 3 key signals, each citing a specific number from the data above. Tag each as "BULLISH", "NEUTRAL", or "BEARISH".
            3. List up to 2 risk factors based on the data.
            4. Provide a 1-2 sentence probabilistic outlook.

            OUTPUT: Respond ONLY with valid JSON matching this exact schema. No markdown, no preamble, no explanation outside the JSON:
            {
              "summary": "string",
              "signals": [{"text": "string", "metric": "string", "sentiment": "BULLISH|NEUTRAL|BEARISH"}],
              "riskFactors": ["string"],
              "outlook": "string"
            }

            CONSTRAINTS:
            - Do NOT predict specific price targets or future prices.
            - Do NOT use words "should", "must", or "will definitely".
            - Do NOT reference any data not provided above.
            - Do NOT recommend buying or selling.
            - Maximum 3 signals, maximum 2 risk factors.
            - Keep total response under 300 tokens.
            """,
            coinData.get("name"),
            coinData.get("name"), coinData.get("symbol"),
            coinData.get("currentPrice"),
            coinData.get("priceChangePercentage24h"),
            coinData.get("marketCap"),
            coinData.get("totalVolume"));
    }

    // ========== Feature 2: Portfolio Risk ==========

    @SuppressWarnings("unchecked")
    public PortfolioRiskResponse getPortfolioRisk(Long userId) {
        if (!aiEnabled) return null;

        String cacheKey = PROMPT_VERSION + ":portfolio:" + userId;
        PortfolioRiskResponse cached = portfolioRiskCache.get(cacheKey);
        if (cached != null) return cached;

        Map<String, Object> portfolio = tradingService.getPortfolio(userId);
        List<Map<String, Object>> holdings = (List<Map<String, Object>>) portfolio.get("holdings");
        if (holdings == null || holdings.isEmpty()) return null;

        BigDecimal totalValue = new BigDecimal(portfolio.get("totalPortfolioValue").toString());
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) return null;

        // Compute HHI
        BigDecimal hhi = BigDecimal.ZERO;
        BigDecimal maxWeight = BigDecimal.ZERO;
        String topCoinId = "";

        for (Map<String, Object> h : holdings) {
            BigDecimal value = new BigDecimal(h.get("currentValue").toString());
            BigDecimal weight = value.divide(totalValue, 6, RoundingMode.HALF_UP);
            hhi = hhi.add(weight.multiply(weight));
            if (weight.compareTo(maxWeight) > 0) {
                maxWeight = weight;
                topCoinId = (String) h.get("coinId");
            }
        }

        // Compute weighted volatility
        BigDecimal weightedVol = BigDecimal.ZERO;
        for (Map<String, Object> h : holdings) {
            BigDecimal value = new BigDecimal(h.get("currentValue").toString());
            BigDecimal weight = value.divide(totalValue, 6, RoundingMode.HALF_UP);
            BigDecimal change = marketDataService.getPriceChangePercentage24h((String) h.get("coinId"));
            if (change != null) {
                weightedVol = weightedVol.add(weight.multiply(change.abs()));
            }
        }

        BigDecimal topPct = maxWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);

        PortfolioRiskResponse response = new PortfolioRiskResponse();
        response.setHhiIndex(hhi.setScale(4, RoundingMode.HALF_UP));
        response.setTopHoldingPercentage(topPct);
        response.setTopHoldingCoinId(topCoinId);
        response.setWeightedVolatility(weightedVol.setScale(2, RoundingMode.HALF_UP));

        if (!checkBudget(userId, 800)) {
            return response; // metrics-only
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("hhi", hhi);
        metrics.put("topHoldingPercentage", topPct);
        metrics.put("topCoinId", topCoinId);
        metrics.put("weightedVolatility", weightedVol);
        metrics.put("holdingCount", holdings.size());
        metrics.put("totalValue", totalValue);

        String prompt = buildPortfolioRiskPrompt(metrics);
        long start = System.currentTimeMillis();

        try {
            String rawResponse = claudeSonnet.generate(prompt, 800);
            long duration = System.currentTimeMillis() - start;

            String json = extractJson(rawResponse);
            PortfolioRiskResponse aiResult = objectMapper.readValue(json, PortfolioRiskResponse.class);

            if (aiResult != null) {
                response.setRiskScore(aiResult.getRiskScore());
                response.setRiskJustification(aiResult.getRiskJustification());
                response.setConcentrationNarrative(aiResult.getConcentrationNarrative());
                response.setVolatilityNarrative(aiResult.getVolatilityNarrative());
                response.setActionableInsight(aiResult.getActionableInsight());
                recordUsage(userId, 800, "portfolio-risk", claudeSonnet.getProviderName(), (int) duration);
            }
        } catch (Exception e) {
            log.warn("Portfolio risk AI call failed: {}", e.getMessage());
        }

        portfolioRiskCache.put(cacheKey, response);
        return response;
    }

    private String buildPortfolioRiskPrompt(Map<String, Object> metrics) {
        return String.format("""
            ROLE: You are a portfolio risk analyst. You provide data-grounded assessments. \
            You never fabricate data or reference information not provided to you.

            TASK: Assess the risk profile of this cryptocurrency portfolio.

            DATA:
            - HHI (Herfindahl-Hirschman Index): %s (0=perfectly diversified, 1=single asset)
            - Top Holding: %s at %s%% of portfolio
            - Weighted Volatility (24h): %s%%
            - Number of Holdings: %s
            - Total Portfolio Value: $%s

            INSTRUCTIONS:
            1. Assign a risk score from 1 (lowest risk) to 10 (highest risk).
            2. Provide a 1-sentence justification for the score.
            3. Write a 1-sentence concentration narrative.
            4. Write a 1-sentence volatility narrative.
            5. Provide one actionable insight starting with "Consider" — never use "should" or "must".

            OUTPUT: Respond ONLY with valid JSON:
            {
              "riskScore": number,
              "riskJustification": "string",
              "concentrationNarrative": "string",
              "volatilityNarrative": "string",
              "actionableInsight": "string"
            }

            CONSTRAINTS:
            - Do NOT predict future prices or portfolio values.
            - Do NOT use "should", "must", or "will definitely".
            - Reference only the data provided above.
            - Keep total response under 250 tokens.
            """,
            metrics.get("hhi"), metrics.get("topCoinId"), metrics.get("topHoldingPercentage"),
            metrics.get("weightedVolatility"), metrics.get("holdingCount"), metrics.get("totalValue"));
    }

    // ========== Feature 3: Trade Reasoning ==========

    public TradeReasoningResponse getTradeReasoning(String coinId, String action, Long userId) {
        if (!aiEnabled) return null;

        String cacheKey = PROMPT_VERSION + ":trade:" + coinId + ":" + action;
        TradeReasoningResponse cached = tradeReasoningCache.get(cacheKey);
        if (cached != null) return cached;

        if (!checkBudget(userId, 600)) return null;

        Map<String, Object> coinData = findCoinData(coinId);
        if (coinData == null) return null;

        String prompt = buildTradeReasoningPrompt(coinData, action);
        long start = System.currentTimeMillis();

        try {
            String rawResponse = claudeSonnet.generate(prompt, 600);
            long duration = System.currentTimeMillis() - start;

            String json = extractJson(rawResponse);
            TradeReasoningResponse response = objectMapper.readValue(json, TradeReasoningResponse.class);

            if (response != null) {
                response.setDisclaimer("This is AI-generated analysis for informational purposes only. " +
                        "It does not constitute financial advice. Always do your own research.");
                tradeReasoningCache.put(cacheKey, response);
                recordUsage(userId, 600, "trade-reasoning", claudeSonnet.getProviderName(), (int) duration);
            }
            return response;
        } catch (Exception e) {
            log.warn("Trade reasoning failed for {} {}: {}", action, coinId, e.getMessage());
            return null;
        }
    }

    private String buildTradeReasoningPrompt(Map<String, Object> coinData, String action) {
        return String.format("""
            ROLE: You are a neutral market data analyst. You never recommend actions. \
            You never fabricate data or reference information not provided to you.

            TASK: Provide contextual reasoning for a user considering a %s position on %s.

            DATA:
            - Coin: %s (%s)
            - Current Price: $%s
            - 24h Price Change: %s%%
            - Market Cap: $%s
            - 24h Trading Volume: $%s

            INSTRUCTIONS:
            1. Write 2 sentences of context, each citing a specific metric from the data above.
            2. Write exactly 2 bullet-point considerations relevant to this %s action.
            3. Assign a sentiment tag: "Data Supportive", "Data Neutral", or "Data Cautionary".

            OUTPUT: Respond ONLY with valid JSON:
            {
              "context": "string",
              "considerations": ["string", "string"],
              "sentiment": "Data Supportive|Data Neutral|Data Cautionary"
            }

            CONSTRAINTS:
            - NEVER use the words "buy", "sell", "should", "must", or "recommend".
            - Do NOT predict future prices.
            - Frame all considerations as observations, not directives.
            - Reference only the provided data.
            - Keep total response under 200 tokens.
            """,
            action.toLowerCase(), coinData.get("name"),
            coinData.get("name"), coinData.get("symbol"),
            coinData.get("currentPrice"),
            coinData.get("priceChangePercentage24h"),
            coinData.get("marketCap"),
            coinData.get("totalVolume"),
            action.toLowerCase());
    }

    // ========== Feature 4: Alert Narratives ==========

    public List<String> generateAlertNarratives(List<Map<String, Object>> alertBatch) {
        if (!aiEnabled || alertBatch.isEmpty()) return List.of();

        StringBuilder dataSection = new StringBuilder();
        for (int i = 0; i < alertBatch.size(); i++) {
            Map<String, Object> a = alertBatch.get(i);
            dataSection.append(String.format("%d. Coin: %s | Pattern: %s | Title: %s | Metrics: %s\n",
                    i + 1, a.get("coinId"), a.get("patternType"), a.get("title"), a.get("metricData")));
        }

        String prompt = String.format("""
            ROLE: You are a concise market alert narrator.

            TASK: Write a one-sentence narrative for each pattern alert below.

            DATA:
            %s

            OUTPUT: Respond ONLY with a JSON array of strings, one narrative per alert:
            ["narrative1", "narrative2", ...]

            CONSTRAINTS:
            - Each narrative must be exactly one sentence.
            - Reference the specific numbers from the metrics.
            - Do NOT predict prices or recommend actions.
            - Keep each narrative under 30 words.
            """, dataSection);

        try {
            String rawResponse = claudeHaiku.generate(prompt, 300);
            String json = extractJson(rawResponse);
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("Alert narrative generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ========== Alert Management ==========

    public List<PatternAlertResponse> getAlerts(Long userId) {
        List<PatternAlert> alerts = patternAlertRepository.findTop50ByOrderByCreatedAtDesc();
        Set<Long> readIds = userAlertReadRepository.findByUserId(userId).stream()
                .map(UserAlertRead::getAlertId)
                .collect(Collectors.toSet());

        return alerts.stream().map(a -> {
            PatternAlertResponse r = new PatternAlertResponse();
            r.setId(a.getId());
            r.setCoinId(a.getCoinId());
            r.setPatternType(a.getPatternType());
            r.setSeverity(a.getSeverity());
            r.setTitle(a.getTitle());
            r.setNarrative(a.getNarrative());
            r.setMetricData(a.getMetricData());
            r.setCreatedAt(a.getCreatedAt().toString());
            r.setRead(readIds.contains(a.getId()));
            return r;
        }).toList();
    }

    public long getUnreadAlertCount(Long userId) {
        return userAlertReadRepository.countUnreadByUserId(userId);
    }

    public void markAlertsRead(Long userId, List<Long> alertIds) {
        for (Long alertId : alertIds) {
            if (!userAlertReadRepository.existsByUserIdAndAlertId(userId, alertId)) {
                UserAlertRead read = new UserAlertRead();
                read.setUserId(userId);
                read.setAlertId(alertId);
                userAlertReadRepository.save(read);
            }
        }
    }

    // ========== Health Check ==========

    public Map<String, String> checkProviderHealth() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("gemini", checkProvider(geminiProvider, "Say OK"));
        status.put("claudeSonnet", checkProvider(claudeSonnet, "Say OK"));
        status.put("claudeHaiku", checkProvider(claudeHaiku, "Say OK"));
        return status;
    }

    private String checkProvider(AiProvider provider, String testPrompt) {
        try {
            String result = provider.generate(testPrompt, 5);
            return result != null && !result.isBlank() ? "ok" : "error";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    // ========== Helpers ==========

    private Map<String, Object> findCoinData(String coinId) {
        return marketDataService.getTop20().stream()
                .filter(c -> coinId.equals(c.get("coinId")))
                .findFirst().orElse(null);
    }

    private String extractJson(String raw) {
        if (raw == null) return null;
        raw = raw.strip();
        // Strip markdown code fences if present
        if (raw.startsWith("```json")) {
            raw = raw.substring(7);
        } else if (raw.startsWith("```")) {
            raw = raw.substring(3);
        }
        if (raw.endsWith("```")) {
            raw = raw.substring(0, raw.length() - 3);
        }
        return raw.strip();
    }

    private boolean checkBudget(Long userId, int estimatedTokens) {
        try {
            Optional<AiUsage> usage = aiUsageRepository.findByUserIdAndUsageDate(userId, LocalDate.now());
            if (usage.isPresent() && usage.get().getTokensUsed() + estimatedTokens > dailyTokensPerUser) {
                log.debug("Daily token budget exceeded for user {}", userId);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Budget check failed: {}", e.getMessage());
            return true; // fail open
        }
    }

    private void recordUsage(Long userId, int tokensUsed, String endpoint, String provider, int durationMs) {
        try {
            // Update daily aggregate
            AiUsage usage = aiUsageRepository.findByUserIdAndUsageDate(userId, LocalDate.now())
                    .orElseGet(() -> {
                        AiUsage u = new AiUsage();
                        u.setUserId(userId);
                        u.setUsageDate(LocalDate.now());
                        u.setTokensUsed(0);
                        u.setRequestCount(0);
                        return u;
                    });
            usage.setTokensUsed(usage.getTokensUsed() + tokensUsed);
            usage.setRequestCount(usage.getRequestCount() + 1);
            aiUsageRepository.save(usage);

            // Append to request log
            AiRequestLog logEntry = new AiRequestLog();
            logEntry.setUserId(userId);
            logEntry.setEndpoint(endpoint);
            logEntry.setProvider(provider);
            logEntry.setModel(provider);
            logEntry.setTokensUsed(tokensUsed);
            logEntry.setDurationMs(durationMs);
            aiRequestLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to record AI usage: {}", e.getMessage());
        }
    }
}
