package ru.bell.autoleasing.agent.router;

import java.util.Set;

public record RoutingDecision(Set<String> allowedTools, String rule) {
}
