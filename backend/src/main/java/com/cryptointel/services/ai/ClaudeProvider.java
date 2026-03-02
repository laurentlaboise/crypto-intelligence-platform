package com.cryptointel.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

public class ClaudeProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public ClaudeProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Claude API key not configured — Claude provider ({}) will be unavailable", model);
        }
    }

    @Override
    public String generate(String prompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("Claude API key not configured");
        }

        String url = "https://api.anthropic.com/v1/messages";

        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", maxTokens,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        return callWithRetry(url, body);
    }

    private String callWithRetry(String url, Map<String, Object> body) {
        try {
            return doCall(url, body);
        } catch (AiProviderException e) {
            if (isRetryable(e)) {
                log.info("Claude call failed with retryable error, retrying in 1s...");
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return doCall(url, body);
            }
            throw e;
        }
    }

    private String doCall(String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                String text = content.get(0).path("text").asText();
                if (text != null && !text.isBlank()) {
                    return text.strip();
                }
            }
            throw new AiProviderException("Claude returned empty response");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new AiProviderException("Claude API error " + e.getStatusCode() + ": " + e.getMessage(), e);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Claude call failed: " + e.getMessage(), e);
        }
    }

    private boolean isRetryable(AiProviderException e) {
        Throwable cause = e.getCause();
        if (cause instanceof HttpClientErrorException hce) {
            return hce.getStatusCode().value() == 429;
        }
        if (cause instanceof HttpServerErrorException hse) {
            int code = hse.getStatusCode().value();
            return code == 500 || code == 503;
        }
        return false;
    }

    @Override
    public String getProviderName() {
        return "claude-" + model;
    }
}
