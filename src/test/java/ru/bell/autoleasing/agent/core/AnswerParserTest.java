package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerParserTest {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final AnswerParser parser = new AnswerParser(mapper);
    private final UUID traceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String VALID = """
            {"answer":"Оплачено 20 030 руб.","figures":[{"label":"Оплачено","value":20030.00,"unit":"RUB","period":"2026-09"}],"warnings":[]}
            """;

    @Test
    void validAnswerGetsServerTraceIdAndTools() {
        AnswerParser.Result r = parser.parse(VALID, traceId, List.of("payments_summary"));
        assertThat(r.valid()).isTrue();
        assertThat(r.answer().traceId()).isEqualTo(traceId.toString());
        assertThat(r.answer().toolsUsed()).containsExactly("payments_summary");
        assertThat(r.answer().figures().get(0).value()).isEqualByComparingTo("20030.00");
    }

    @Test
    void toolsClaimedByModelAreReplaced() {
        String claimed = "{\"answer\":\"a\",\"figures\":[],\"warnings\":[],\"tools_used\":[\"schedule\"],\"trace_id\":\"x\"}";
        AnswerParser.Result r = parser.parse(claimed, traceId, List.of());
        assertThat(r.valid()).isTrue();
        assertThat(r.answer().toolsUsed()).isEmpty();
        assertThat(r.answer().traceId()).isEqualTo(traceId.toString());
    }

    @Test
    void codeFenceIsAccepted() {
        assertThat(parser.parse("```json\n" + VALID + "```", traceId, List.of()).valid()).isTrue();
    }

    @Test
    void proseGivesFallback() {
        AnswerParser.Result r = parser.parse("Вы заплатили 20 030 рублей.", traceId, List.of("payments_summary"));
        assertThat(r.valid()).isFalse();
        assertThat(r.answer().answer()).isEqualTo(AnswerParser.FALLBACK_TEXT);
        assertThat(r.answer().warnings()).containsExactly(AnswerParser.INVALID_OUTPUT_WARNING);
        assertThat(r.answer().toolsUsed()).containsExactly("payments_summary");
    }

    @Test
    void schemaViolationsGiveFallback() {
        String[] invalid = {
                "{\"answer\":\"a\",\"figures\":[]}",
                "{\"answer\":\"a\",\"figures\":[{\"label\":\"x\",\"value\":\"20030\",\"unit\":\"RUB\"}],\"warnings\":[]}",
                "{\"answer\":\"a\",\"figures\":[],\"warnings\":[],\"advice\":\"купите акции\"}",
                "{\"answer\":\"\",\"figures\":[],\"warnings\":[]}",
                "[]", "", "null"};
        for (String content : invalid) {
            AnswerParser.Result r = parser.parse(content, traceId, List.of());
            assertThat(r.valid()).as(content).isFalse();
            assertThat(r.error()).as(content).isNotBlank();
        }
    }
}
