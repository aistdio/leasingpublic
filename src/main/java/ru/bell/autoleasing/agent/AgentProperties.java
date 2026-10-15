package ru.bell.autoleasing.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("autolease.agent")
public record AgentProperties(int maxSteps, int questionMaxLength, String promptVersion, Llm llm) {
    public record Llm(String provider, String model, String baseUrl, int numCtx, double temperature, int timeoutSeconds) {
    }
}
