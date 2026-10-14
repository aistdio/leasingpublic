package ru.bell.autoleasing.agent.router;

import org.springframework.stereotype.Component;
import ru.bell.autoleasing.agent.tool.ContractsOverviewTool;
import ru.bell.autoleasing.agent.tool.PaymentsSummaryTool;
import ru.bell.autoleasing.agent.tool.ScheduleTool;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class ToolRouter {
    private record Rule(String name, Pattern pattern, String tool) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("PAYMENTS", Pattern.compile("оплат|оплач|заплат|платил|списал|начисл"), PaymentsSummaryTool.NAME),
            new Rule("SCHEDULE", Pattern.compile("график|следующ|ближайш|когда\s.*плат|срок"), ScheduleTool.NAME),
            new Rule("CONTRACTS", Pattern.compile("договор|машин|автомоб|долг|остат"), ContractsOverviewTool.NAME));

    private static final Set<String> ALL_TOOLS = Collections.unmodifiableSet(new TreeSet<>(
            List.of(PaymentsSummaryTool.NAME, ScheduleTool.NAME, ContractsOverviewTool.NAME)));

    public RoutingDecision route(String question) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT).replace('ё', 'е');
        Set<String> tools = new TreeSet<>();
        List<String> matched = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                tools.add(rule.tool());
                matched.add(rule.name());
            }
        }
        if (tools.isEmpty()) {
            return new RoutingDecision(ALL_TOOLS, "FALLBACK_ALL");
        }
        return new RoutingDecision(Collections.unmodifiableSet(tools), String.join("+", matched));
    }
}
