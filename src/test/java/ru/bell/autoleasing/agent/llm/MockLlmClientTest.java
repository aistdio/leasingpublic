package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import ru.bell.autoleasing.agent.tool.ToolSpec;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZoneId.of("Europe/Moscow"));
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ObjectReader exactReader = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
    private final MockLlmClient client = new MockLlmClient(clock, mapper);
    private final List<ToolSpec> paymentsTool = List.of(ToolSpec.of("payments_summary", "d", List.of()));

    @Test
    void firstTurnAsksForPreviousMonth() {
        LlmResponse r = client.chat(new LlmRequest(List.of(LlmMessage.system("s"),
                LlmMessage.user("Сколько я заплатил в прошлом месяце?")), paymentsTool));

        assertThat(r.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("payments_summary");
            assertThat(call.arguments()).containsEntry("period_from", "2026-09-01").containsEntry("period_to", "2026-09-30");
        });
    }

    @Test
    void firstTurnDefaultsToCurrentMonth() {
        LlmResponse r = client.chat(new LlmRequest(List.of(LlmMessage.user("Сколько я заплатил?")), paymentsTool));
        assertThat(r.toolCalls().get(0).arguments()).containsEntry("period_from", "2026-10-01").containsEntry("period_to", "2026-10-31");
    }

    @Test
    void secondTurnBuildsJsonAnswerFromToolResults() throws Exception {
        String toolResult = "{\"periodFrom\":\"2026-09-01\",\"periodTo\":\"2026-09-30\",\"paidTotal\":20030.00,"
                + "\"paidCount\":2,\"accruedTotal\":10000.00,\"accruedCount\":1,\"currency\":\"RUB\"}";
        LlmRequest request = new LlmRequest(List.of(LlmMessage.system("s"), LlmMessage.user("Сколько я заплатил в прошлом месяце?"),
                LlmMessage.assistant("", List.of(new LlmToolCall("payments_summary", java.util.Map.of()))),
                LlmMessage.tool("payments_summary", toolResult)), paymentsTool);

        LlmResponse r = client.chat(request);

        assertThat(r.hasToolCalls()).isFalse();
        assertThat(r.content()).contains("\"value\":20030.00").contains("20030.00 руб.");
        JsonNode answer = exactReader.readTree(r.content());
        assertThat(answer.path("figures").get(0).path("label").asText()).isEqualTo("Оплачено");
        assertThat(answer.path("figures").get(0).path("value").decimalValue().toPlainString()).isEqualTo("20030.00");
        assertThat(answer.path("answer").asText()).contains("20030.00 руб.");
        assertThat(answer.path("warnings").isArray()).isTrue();
        assertThat(client.chat(request).content()).isEqualTo(r.content());
    }

    @Test
    void largeRoundAmountKeepsTwoDecimalPlacesWithoutExponent() throws Exception {
        LlmRequest request = new LlmRequest(List.of(LlmMessage.user("Какой у меня остаток долга?"),
                LlmMessage.assistant("", List.of(new LlmToolCall("contracts_overview", java.util.Map.of()))),
                LlmMessage.tool("contracts_overview", "{\"contracts\":[],\"totalRemainingDebt\":12000000.00}")),
                List.of(ToolSpec.of("contracts_overview", "d", List.of())));

        JsonNode answer = exactReader.readTree(client.chat(request).content());

        assertThat(answer.path("figures").get(0).path("label").asText()).isEqualTo("Остаток долга");
        assertThat(answer.path("figures").get(0).path("value").decimalValue().toPlainString()).isEqualTo("12000000.00");
        assertThat(answer.path("answer").asText()).contains("12000000.00 руб.");
    }
}
