package ru.bell.autoleasing.agent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.cabinet.ClientFinanceQueryService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentsSummaryTool implements AgentTool {
    public static final String NAME = "payments_summary";
    private static final ToolSpec SPEC = ToolSpec.of(NAME,
            "Сколько клиент оплатил и сколько ему начислено к оплате за период (даты включительно).",
            List.of(new ToolSpec.Param("period_from", "string", "Начало периода, ГГГГ-ММ-ДД", true),
                    new ToolSpec.Param("period_to", "string", "Конец периода, ГГГГ-ММ-ДД", true)));

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
        LocalDate from = ToolArgs.requiredDate(arguments, "period_from");
        LocalDate to = ToolArgs.requiredDate(arguments, "period_to");
        if (from.isAfter(to)) {
            throw new ToolArgumentException("period_from не может быть позже period_to");
        }
        return finance.summarize(context.clientId(), from, to);
    }
}
