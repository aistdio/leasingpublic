package ru.bell.autoleasing.agent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.cabinet.ClientFinanceQueryService;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScheduleTool implements AgentTool {
    public static final String NAME = "schedule";
    private static final ToolSpec SPEC = ToolSpec.of(NAME,
            "Ближайшие неоплаченные платежи клиента по графику, следующий платёж и просроченные платежи.",
            List.of(new ToolSpec.Param("contract_id", "integer", "Номер договора (id); если не указан — по всем договорам", false),
                    new ToolSpec.Param("limit", "integer", "Сколько платежей показать, от 1 до 12, по умолчанию 3", false)));

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
        Long contractId = ToolArgs.optionalLong(arguments, "contract_id");
        int limit = ToolArgs.optionalInt(arguments, "limit", 3, 1, 12);
        return finance.findUpcoming(context.clientId(), contractId, limit, context.today());
    }
}
