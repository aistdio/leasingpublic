package ru.bell.autoleasing.agent.trace.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TraceSummary(UUID id, String username, String question, String status, Long latencyMs, int totalTokens,
                           OffsetDateTime createdAt) {
}
