package ru.bell.autoleasing.agent.llm;

import ru.bell.autoleasing.agent.tool.ToolSpec;

import java.util.List;

public record LlmRequest(List<LlmMessage> messages, List<ToolSpec> tools) {
}
