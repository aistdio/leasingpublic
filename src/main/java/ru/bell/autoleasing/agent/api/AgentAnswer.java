package ru.bell.autoleasing.agent.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AgentAnswer(String answer, List<Figure> figures, @JsonProperty("tools_used") List<String> toolsUsed,
                          @JsonProperty("trace_id") String traceId, List<String> warnings) {
}
