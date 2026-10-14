package ru.bell.autoleasing.agent.tool;

import ru.bell.autoleasing.cabinet.dto.ContractSummary;

import java.math.BigDecimal;
import java.util.List;

public record ContractsOverview(List<ContractSummary> contracts, BigDecimal totalRemainingDebt) {
}
