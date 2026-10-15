package ru.bell.autoleasing.agent.llm;

import java.util.List;

public record LlmMessage(String role, String content, List<LlmToolCall> toolCalls, String toolName) {
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, List.of(), null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, List.of(), null);
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls == null ? List.of() : List.copyOf(toolCalls), null);
    }

    public static LlmMessage tool(String toolName, String content) {
        return new LlmMessage("tool", content, List.of(), toolName);
    }
}
