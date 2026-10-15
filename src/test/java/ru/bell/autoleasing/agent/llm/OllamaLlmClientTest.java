package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.tool.ToolSpec;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaLlmClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AgentProperties.Llm props = new AgentProperties.Llm("ollama", "qwen3:8b", "http://ollama.test", 8192, 0.0, 30);
    private final OllamaLlmClient client = new OllamaLlmClient(builder, props, new ObjectMapper());

    private static LlmRequest scheduleQuestion() {
        return new LlmRequest(List.of(LlmMessage.system("s"), LlmMessage.user("Когда платить?")),
                List.of(ToolSpec.of("schedule", "d", List.of())));
    }

    @Test
    void sendsModelOptionsAndToolsAndParsesToolCall() {
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen3:8b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.think").value(false))
                .andExpect(jsonPath("$.options.num_ctx").value(8192))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("schedule"))
                .andExpect(jsonPath("$.messages[1].content").value("Когда платить?"))
                .andRespond(withSuccess("""
                        {"model":"qwen3:8b","message":{"role":"assistant","content":"",
                         "tool_calls":[{"function":{"name":"schedule","arguments":{"limit":2}}}]},
                         "done":true,"prompt_eval_count":321,"eval_count":12}
                        """, MediaType.APPLICATION_JSON));

        LlmResponse response = client.chat(scheduleQuestion());

        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls().get(0).name()).isEqualTo("schedule");
        assertThat(response.toolCalls().get(0).arguments()).containsEntry("limit", 2);
        assertThat(response.promptTokens()).isEqualTo(321);
        assertThat(response.completionTokens()).isEqualTo(12);
    }

    @Test
    void sendsToolResultsBackWithToolName() {
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.name").value("schedule"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_name").value("schedule"))
                .andRespond(withSuccess("""
                        {"model":"qwen3:8b","message":{"role":"assistant","content":"{\\"answer\\":\\"ok\\"}"},"done":true}
                        """, MediaType.APPLICATION_JSON));

        LlmResponse response = client.chat(new LlmRequest(List.of(LlmMessage.system("s"), LlmMessage.user("q"),
                LlmMessage.assistant("", List.of(new LlmToolCall("schedule", Map.of()))),
                LlmMessage.tool("schedule", "{}")), List.of()));

        assertThat(response.hasToolCalls()).isFalse();
        assertThat(response.content()).isEqualTo("{\"answer\":\"ok\"}");
    }

    @Test
    void parsesArgumentsGivenAsString() {
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withSuccess("""
                {"message":{"role":"assistant","content":"",
                 "tool_calls":[{"function":{"name":"schedule","arguments":"{\\"contract_id\\": 7}"}}]},"done":true}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.chat(scheduleQuestion()).toolCalls().get(0).arguments()).containsEntry("contract_id", 7);
    }

    @Test
    void serverErrorAndMissingModelBecomeUnavailable() {
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withServerError());
        assertThatThrownBy(() -> client.chat(scheduleQuestion())).isInstanceOf(LlmUnavailableException.class);

        server.reset();
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"error\":\"model 'qwen3:8b' not found\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.chat(scheduleQuestion())).isInstanceOf(LlmUnavailableException.class);
    }
}
