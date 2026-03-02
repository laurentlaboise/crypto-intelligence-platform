package com.cryptointel.services.ai;

public interface AiProvider {

    String generate(String prompt, int maxTokens);

    String getProviderName();
}
