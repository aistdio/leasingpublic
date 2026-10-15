package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.bell.autoleasing.agent.tool.ContractsOverviewTool;
import ru.bell.autoleasing.agent.tool.PaymentsSummaryTool;
import ru.bell.autoleasing.agent.tool.ScheduleTool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** Deterministic stand-in for the model: for JUnit, continuous integration and load tests. */
public class MockLlmClient implements LlmClient {
    private final Clock clock;
    private final ObjectMapper mapper;

    public MockLlmClient(Clock clock, ObjectMapper mapper) {
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock-1";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        List<LlmMessage> messages = request.messages();
        int lastUser = 0;
        for (int i = 0; i < messages.size(); i++) {
            if ("user".equals(messages.get(i).role())) {
                lastUser = i;
            }
        }
        String question = messages.get(lastUser).content();
        List<LlmMessage> toolResults = messages.subList(lastUser + 1, messages.size()).stream()
                .filter(m -> "tool".equals(m.role())).toList();
        int promptTokens = messages.stream().mapToInt(m -> m.content() == null ? 0 : m.content().length()).sum() / 4;
        if (toolResults.isEmpty() && request.tools() != null && !request.tools().isEmpty()) {
            List<LlmToolCall> calls = request.tools().stream()
                    .map(tool -> new LlmToolCall(tool.name(), argumentsFor(tool.name(), question))).toList();
            return new LlmResponse("", calls, promptTokens, 10 * calls.size(), model());
        }
        String content = finalAnswer(toolResults);
        return new LlmResponse(content, List.of(), promptTokens, content.length() / 4, model());
    }

    private Map<String, Object> argumentsFor(String tool, String question) {
        if (!PaymentsSummaryTool.NAME.equals(tool)) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(clock);
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        LocalDate from;
        LocalDate to;
        if (q.contains("прошл")) {
            YearMonth month = YearMonth.from(today).minusMonths(1);
            from = month.atDay(1);
            to = month.atEndOfMonth();
        } else if (q.contains("год")) {
            from = today.withDayOfYear(1);
            to = today.withDayOfYear(today.lengthOfYear());
        } else {
            YearMonth month = YearMonth.from(today);
            from = month.atDay(1);
            to = month.atEndOfMonth();
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("period_from", from.toString());
        args.put("period_to", to.toString());
        return args;
    }

    private String finalAnswer(List<LlmMessage> toolResults) {
        List<Map<String, Object>> figures = new ArrayList<>();
        for (LlmMessage result : toolResults) {
            JsonNode node = read(result.content());
            if (node.has("error")) {
                continue;
            }
            switch (result.toolName()) {
                case PaymentsSummaryTool.NAME -> {
                    String period = node.path("periodFrom").asText() + " — " + node.path("periodTo").asText();
                    figures.add(figure("Оплачено", node.path("paidTotal").decimalValue(), period));
                    figures.add(figure("Начислено", node.path("accruedTotal").decimalValue(), period));
                }
                case ScheduleTool.NAME -> {
                    JsonNode next = node.path("nextPayment");
                    if (next.isObject()) {
                        figures.add(figure("Следующий платёж", next.path("amount").decimalValue(), next.path("dueDate").asText()));
                    }
                }
                case ContractsOverviewTool.NAME ->
                        figures.add(figure("Остаток долга", node.path("totalRemainingDebt").decimalValue(), null));
                default -> {
                }
            }
        }
        String answer = figures.isEmpty()
                ? "Не удалось найти данные по вашему вопросу."
                : "По вашим данным: " + figures.stream().map(f -> f.get("label") + " — " + ((BigDecimal) f.get("value")).toPlainString() + " руб.")
                .collect(Collectors.joining("; ")) + ".";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", answer);
        body.put("figures", figures);
        body.put("warnings", List.of());
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> figure(String label, BigDecimal value, String period) {
        Map<String, Object> figure = new LinkedHashMap<>();
        figure.put("label", label);
        figure.put("value", value == null ? null : value.setScale(2, RoundingMode.HALF_UP));
        figure.put("unit", "RUB");
        figure.put("period", period);
        return figure;
    }

    private JsonNode read(String json) {
        try {
            return mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode().put("error", "invalid tool result");
        }
    }
}
