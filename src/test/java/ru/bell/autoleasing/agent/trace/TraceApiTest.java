package ru.bell.autoleasing.agent.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TraceApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TraceRecorder recorder;

    private UUID recordTrace(String question) {
        UUID id = UUID.randomUUID();
        recorder.start(id, 1001, "client1001", question, "mock", "mock-1", "v1", "req-1", "test");
        recorder.step(id, 1, StepType.ROUTING, null, "[\"schedule\"]", "SCHEDULE", null, 0, 0, 0);
        recorder.step(id, 2, StepType.TOOL_CALL, "schedule", "{}", "{\"nextPayment\":null}", null, 12, 0, 0);
        recorder.finish(id, TraceStatus.OK, "SCHEDULE", "{\"answer\":\"Платежей нет\"}", 100, 20, 345, null);
        return id;
    }

    @Test
    void managerReadsTraceWithSteps() throws Exception {
        UUID id = recordTrace("Когда платить?");
        mockMvc.perform(get("/api/agent/traces/" + id).with(user(TestPrincipals.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.answer.answer").value("Платежей нет"))
                .andExpect(jsonPath("$.routingRule").value("SCHEDULE"))
                .andExpect(jsonPath("$.promptTokens").value(100))
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[1].toolName").value("schedule"));
    }

    @Test
    void listShowsRecentTraces() throws Exception {
        recordTrace("Первый вопрос");
        recordTrace("Второй вопрос");
        mockMvc.perform(get("/api/agent/traces").param("limit", "10").with(user(TestPrincipals.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].totalTokens").value(120));
    }

    @Test
    void clientCannotReadTraces() throws Exception {
        UUID id = recordTrace("Когда платить?");
        mockMvc.perform(get("/api/agent/traces/" + id).with(user(TestPrincipals.client(1001))))
                .andExpect(status().isForbidden());
    }

    @Test
    void badRequestsAreRejected() throws Exception {
        var manager = user(TestPrincipals.manager());
        mockMvc.perform(get("/api/agent/traces/not-a-uuid").with(manager)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/agent/traces/" + UUID.randomUUID()).with(manager)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agent/traces").param("limit", "0").with(manager)).andExpect(status().isBadRequest());
    }
}
