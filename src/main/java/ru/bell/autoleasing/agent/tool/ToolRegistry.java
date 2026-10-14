package ru.bell.autoleasing.agent.tool;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new TreeMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        tools.forEach(tool -> this.tools.put(tool.name(), tool));
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name));
    }

    public List<ToolSpec> specs(Collection<String> names) {
        return names.stream().sorted().map(tools::get).filter(Objects::nonNull).map(AgentTool::spec).toList();
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}
