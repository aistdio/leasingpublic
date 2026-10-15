package ru.bell.autoleasing.agent.api;

import java.math.BigDecimal;

public record Figure(String label, BigDecimal value, String unit, String period) {
}
