package ru.bell.autoleasing.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AgentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String ask(String question) throws Exception {
        return mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.client(1001)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AskRequest(question))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void answersPreviousMonthPaymentsWithMockModel() throws Exception {
        JsonNode answer = mapper.readTree(ask("Сколько я заплатил в прошлом месяце?"));

        assertThat(answer.path("tools_used").get(0).asText()).isEqualTo("payments_summary");
        assertThat(answer.path("figures").get(0).path("value").decimalValue()).isEqualByComparingTo("20030.00");
        assertThat(answer.path("warnings").size()).isZero();
        UUID traceId = UUID.fromString(answer.path("trace_id").asText());
        assertThat(jdbc.queryForObject("SELECT status FROM autoleasing_agent.traces WHERE id = ?", String.class, traceId)).isEqualTo("OK");
        assertThat(jdbc.queryForList("SELECT step_type FROM autoleasing_agent.trace_steps WHERE trace_id = ? ORDER BY step_no",
                String.class, traceId)).containsExactly("ROUTING", "LLM_CALL", "TOOL_CALL", "LLM_CALL");
    }

    @Test
    void managerSeesTheTraceOfAnAnswer() throws Exception {
        String traceId = mapper.readTree(ask("Когда следующий платёж?")).path("trace_id").asText();
        mockMvc.perform(get("/api/agent/traces/" + traceId).with(user(TestPrincipals.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.username").value("client1001"))
                .andExpect(jsonPath("$.steps.length()").value(4));
    }

    @Test
    void blankQuestionIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.client(1001)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerCannotAsk() throws Exception {
        mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.manager()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Когда платить?\"}"))
                .andExpect(status().isForbidden());
    }
}
