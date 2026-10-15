package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.api.AgentAnswer;
import ru.bell.autoleasing.agent.llm.*;
import ru.bell.autoleasing.agent.router.ToolRouter;
import ru.bell.autoleasing.agent.tool.AgentTool;
import ru.bell.autoleasing.agent.tool.ToolRegistry;
import ru.bell.autoleasing.agent.tool.ToolSpec;
import ru.bell.autoleasing.agent.trace.StepType;
import ru.bell.autoleasing.agent.trace.TraceRecorder;
import ru.bell.autoleasing.agent.trace.TraceStatus;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.support.TestPrincipals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {
    private static final String FINAL = "{\"answer\":\"Следующий платёж 31.10.2026\",\"figures\":[{\"label\":\"Следующий платёж\","
            + "\"value\":10000.00,\"unit\":\"RUB\",\"period\":\"2026-10-31\"}],\"warnings\":[]}";

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final LlmClient llm = mock(LlmClient.class);
    private final TraceRecorder traces = mock(TraceRecorder.class);
    private final AgentTool scheduleTool = mock(AgentTool.class);
    private final AgentTool paymentsTool = mock(AgentTool.class);
    private final AgentProperties props = new AgentProperties(5, 1000, "v1",
            new AgentProperties.Llm("mock", "mock-1", "http://unused", 8192, 0.0, 30));
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(scheduleTool.name()).thenReturn("schedule");
        when(scheduleTool.spec()).thenReturn(ToolSpec.of("schedule", "d", List.of()));
        when(paymentsTool.name()).thenReturn("payments_summary");
        when(paymentsTool.spec()).thenReturn(ToolSpec.of("payments_summary", "d", List.of()));
        when(llm.provider()).thenReturn("mock");
        when(llm.model()).thenReturn("mock-1");
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZoneId.of("Europe/Moscow"));
        orchestrator = new AgentOrchestrator(llm, new ToolRouter(), new ToolRegistry(List.of(scheduleTool, paymentsTool)),
                traces, new AnswerParser(mapper), new SystemPrompt(props), props, mapper, clock, "test");
    }

    private static LlmResponse toolCall(String tool, Map<String, Object> args) {
        return new LlmResponse("", List.of(new LlmToolCall(tool, args)), 50, 5, "mock-1");
    }

    private static LlmResponse text(String content) {
        return new LlmResponse(content, List.of(), 80, 30, "mock-1");
    }

    @Test
    void runsToolThenReturnsValidatedAnswer() {
        when(llm.chat(any())).thenReturn(toolCall("schedule", Map.of()), text(FINAL));
        when(scheduleTool.execute(any(), any())).thenReturn(Map.of("nextPayment", Map.of("amount", 10000.00)));

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        assertThat(answer.toolsUsed()).containsExactly("schedule");
        assertThat(answer.warnings()).isEmpty();
        assertThat(answer.figures()).hasSize(1);
        verify(traces).finish(any(), eq(TraceStatus.OK), eq("SCHEDULE"), anyString(), eq(130), eq(35), anyLong(), isNull());
        verify(traces).step(any(), eq(3), eq(StepType.TOOL_CALL), eq("schedule"), anyString(), anyString(), isNull(), anyLong(), eq(0), eq(0));
    }

    @Test
    void clientIdComesFromPrincipalNotFromArguments() {
        when(llm.chat(any())).thenReturn(toolCall("schedule", Map.of("client_id", 1002)), text(FINAL));
        when(scheduleTool.execute(any(), any())).thenReturn(Map.of());

        orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        verify(scheduleTool).execute(any(), argThat(ctx -> ctx.clientId() == 1001L));
    }

    @Test
    void toolOutsideRoutingDecisionIsNotExecuted() {
        when(llm.chat(any())).thenReturn(toolCall("payments_summary", Map.of()), text(FINAL));

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        verify(paymentsTool, never()).execute(any(), any());
        verify(traces).step(any(), anyInt(), eq(StepType.TOOL_CALL), eq("payments_summary"), anyString(), anyString(),
                eq("TOOL_NOT_ALLOWED"), anyLong(), anyInt(), anyInt());
        assertThat(answer.toolsUsed()).isEmpty();
    }

    @Test
    void invalidModelOutputGivesFallback() {
        when(llm.chat(any())).thenReturn(text("Ваш следующий платёж 10 000 рублей"));

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        assertThat(answer.warnings()).containsExactly(AnswerParser.INVALID_OUTPUT_WARNING);
        verify(traces).finish(any(), eq(TraceStatus.INVALID_OUTPUT), anyString(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
    }

    @Test
    void unavailableModelIsReportedWithTraceId() {
        when(llm.chat(any())).thenThrow(new LlmUnavailableException("connection refused", null));

        assertThatThrownBy(() -> orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?"))
                .isInstanceOfSatisfying(AgentUnavailableException.class, e -> assertThat(e.getTraceId()).isNotNull());
        verify(traces).finish(any(), eq(TraceStatus.LLM_ERROR), anyString(), isNull(), anyInt(), anyInt(), anyLong(), contains("connection refused"));
    }

    @Test
    void stopsAfterMaxSteps() {
        when(llm.chat(any())).thenReturn(toolCall("schedule", Map.of()));
        when(scheduleTool.execute(any(), any())).thenReturn(Map.of());

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        verify(llm, times(5)).chat(any());
        assertThat(answer.warnings()).containsExactly(AgentOrchestrator.MAX_STEPS_WARNING);
        verify(traces).finish(any(), eq(TraceStatus.MAX_STEPS), anyString(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
    }

    @Test
    void rejectsBlankAndTooLongQuestionsWithoutCallingTheModel() {
        assertThatThrownBy(() -> orchestrator.ask(TestPrincipals.client(1001), "   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> orchestrator.ask(TestPrincipals.client(1001), "а".repeat(1001))).isInstanceOf(ValidationException.class);
        verifyNoInteractions(llm, traces);
    }
}
