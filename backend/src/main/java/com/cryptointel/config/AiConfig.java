package com.cryptointel.config;

import com.cryptointel.services.ai.ClaudeProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    @Qualifier("claudeSonnet")
    public ClaudeProvider claudeSonnet(
            @Value("${app.ai.claude.api-key:}") String apiKey,
            @Value("${app.ai.claude.model:claude-sonnet-4-20250514}") String model) {
        return new ClaudeProvider(apiKey, model);
    }

    @Bean
    @Qualifier("claudeHaiku")
    public ClaudeProvider claudeHaiku(
            @Value("${app.ai.claude.api-key:}") String apiKey,
            @Value("${app.ai.claude-haiku.model:claude-haiku-4-5-20251001}") String model) {
        return new ClaudeProvider(apiKey, model);
    }
}
