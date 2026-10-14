package ru.bell.autoleasing.agent.tool;

import java.time.LocalDate;

public record ToolContext(long clientId, LocalDate today) {
}
