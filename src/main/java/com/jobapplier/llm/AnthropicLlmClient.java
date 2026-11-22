package com.jobapplier.llm;

import com.jobapplier.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnthropicLlmClient implements LlmClient {
    private final RestClient http;
    private final AppProperties.Llm cfg;

    public AnthropicLlmClient(AppProperties props) {
        this.cfg = props.llm();
        this.http = RestClient.builder().baseUrl(cfg.baseUrl())
            .defaultHeader("x-api-key", cfg.apiKey())
            .defaultHeader("anthropic-version", "2023-06-01").build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String system, String user) {
        Map<String, Object> body = Map.of("model", cfg.model(), "max_tokens", 2000, "system", system,
            "messages", List.of(Map.of("role", "user", "content", user)));
        Map<String, Object> res = http.post().uri("/v1/messages").body(body).retrieve().body(Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
        return (String) content.get(0).get("text");
    }
}
