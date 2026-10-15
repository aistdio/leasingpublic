package ru.bell.autoleasing.agent.llm;

import java.util.Map;

public record LlmToolCall(String name, Map<String, Object> arguments) {
}
