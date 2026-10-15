package ru.bell.autoleasing.agent.trace.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TraceView(UUID id, long clientId, String username, String question, String routingRule, String status,
                        JsonNode answer, String error, String llmProvider, String llmModel, String promptVersion,
                        int promptTokens, int completionTokens, Long latencyMs, String requestId, String appEnv,
                        OffsetDateTime createdAt, OffsetDateTime finishedAt, List<TraceStepView> steps) {
}
