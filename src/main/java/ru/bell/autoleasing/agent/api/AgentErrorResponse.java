package ru.bell.autoleasing.agent.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentErrorResponse(String message, @JsonProperty("trace_id") String traceId) {
}
