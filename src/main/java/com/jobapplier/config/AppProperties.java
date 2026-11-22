package com.jobapplier.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String resumePath, String tailoredDir, Prefs prefs, Limits limits,
                            Schedule schedule, Playwright playwright, Llm llm) {
    public record Prefs(List<String> roles, List<String> locations, int maxAgeDays, double minScore) {}
    public record Limits(int topNPerDay, int maxMailsPerDay, int maxAppliesPerDay, int followUpAfterDays) {}
    public record Schedule(String ingestCron, String followUpCron) {}
    public record Playwright(boolean headless, String sessionDir, int delayMsMin, int delayMsMax) {}
    public record Llm(String apiKey, String model, String baseUrl) {}
}
