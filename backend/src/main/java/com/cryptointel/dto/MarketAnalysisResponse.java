package com.cryptointel.dto;

import java.util.List;

public class MarketAnalysisResponse {

    private String summary;
    private List<Signal> signals;
    private List<String> riskFactors;
    private String outlook;

    public static class Signal {
        private String text;
        private String metric;
        private String sentiment;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }
        public String getSentiment() { return sentiment; }
        public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<Signal> getSignals() { return signals; }
    public void setSignals(List<Signal> signals) { this.signals = signals; }
    public List<String> getRiskFactors() { return riskFactors; }
    public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }
    public String getOutlook() { return outlook; }
    public void setOutlook(String outlook) { this.outlook = outlook; }
}
