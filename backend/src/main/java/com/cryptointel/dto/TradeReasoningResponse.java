package com.cryptointel.dto;

import java.util.List;

public class TradeReasoningResponse {

    private String context;
    private List<String> considerations;
    private String sentiment;
    private String disclaimer;

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public List<String> getConsiderations() { return considerations; }
    public void setConsiderations(List<String> considerations) { this.considerations = considerations; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
}
