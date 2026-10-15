package ru.bell.autoleasing.agent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.agent.llm.LlmClient;
import ru.bell.autoleasing.agent.llm.LlmUnavailableException;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AgentUnavailableEndpointTest {

    @MockitoBean LlmClient llm;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void unavailableModelGives503WithTraceId() throws Exception {
        when(llm.provider()).thenReturn("mock");
        when(llm.model()).thenReturn("mock-1");
        when(llm.chat(any())).thenThrow(new LlmUnavailableException("connection refused", null));

        mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.client(1001)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Когда следующий платёж?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Ассистент временно недоступен, попробуйте позже"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT status FROM autoleasing_agent.traces", String.class)).isEqualTo("LLM_ERROR");
    }
}
