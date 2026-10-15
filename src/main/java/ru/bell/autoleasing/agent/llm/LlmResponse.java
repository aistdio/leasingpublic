package ru.bell.autoleasing.agent.llm;

import java.util.List;

public record LlmResponse(String content, List<LlmToolCall> toolCalls, int promptTokens, int completionTokens, String model) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
