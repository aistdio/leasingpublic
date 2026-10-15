package ru.bell.autoleasing.agent.llm;

public interface LlmClient {
    LlmResponse chat(LlmRequest request);

    String provider();

    String model();
}
