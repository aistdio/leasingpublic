package ru.bell.autoleasing.agent.tool;

import java.util.Map;

public interface AgentTool {
    String name();

    ToolSpec spec();

    Object execute(Map<String, Object> arguments, ToolContext context);
}
