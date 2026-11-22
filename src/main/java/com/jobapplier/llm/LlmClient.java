package com.jobapplier.llm;

public interface LlmClient {
    String complete(String system, String user);
}
