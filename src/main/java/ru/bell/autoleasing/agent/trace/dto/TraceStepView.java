package ru.bell.autoleasing.agent.trace.dto;

public record TraceStepView(int stepNo, String stepType, String toolName, String argumentsJson, String resultJson,
                            String error, long latencyMs, int promptTokens, int completionTokens) {
}
