package com.cryptointel.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class GeminiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GeminiProvider(
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-2.0-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured — Gemini provider will be unavailable");
        }
    }

    @Override
    public String generate(String prompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("Gemini API key not configured");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                + ":generateContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of("maxOutputTokens", maxTokens)
        );

        return callWithRetry(url, body);
    }

    private String callWithRetry(String url, Map<String, Object> body) {
        try {
            return doCall(url, body);
        } catch (AiProviderException e) {
            if (isRetryable(e)) {
                log.info("Gemini call failed with retryable error, retrying in 1s...");
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

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                if (text != null && !text.isBlank()) {
                    return text.strip();
                }
            }
            throw new AiProviderException("Gemini returned empty response");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new AiProviderException("Gemini API error " + e.getStatusCode() + ": " + e.getMessage(), e);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Gemini call failed: " + e.getMessage(), e);
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
        return "gemini-flash";
    }
}
