package ru.bell.autoleasing.agent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.cabinet.ClientFinanceQueryService;
import ru.bell.autoleasing.cabinet.dto.ContractSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContractsOverviewTool implements AgentTool {
    public static final String NAME = "contracts_overview";
    private static final ToolSpec SPEC = ToolSpec.of(NAME,
            "Договоры лизинга клиента: автомобиль, статус, платёж в месяц, остаток долга и общий остаток долга.",
            List.of());

    private final ClientFinanceQueryService finance;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) {
        List<ContractSummary> contracts = finance.findContracts(context.clientId(), context.today());
        BigDecimal total = contracts.stream().map(ContractSummary::remainingDebt)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new ContractsOverview(contracts, total);
    }
}
